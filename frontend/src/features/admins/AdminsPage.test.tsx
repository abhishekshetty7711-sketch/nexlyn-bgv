import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { fakeAuth, mockFetch, OWNER, renderRoutes } from '@/test/testUtils'
import type { AdminView } from './api'
import { AdminsPage } from './AdminsPage'

const ROLES = [
  { id: 'role-super', code: 'SUPER_ADMIN', name: 'Super Admin', description: 'Everything', systemRole: true, permissions: [], memberCount: 1 },
  { id: 'role-analyst', code: 'ANALYST', name: 'Analyst', description: 'Prepares cases', systemRole: true, permissions: [], memberCount: 1 },
]

function admin(overrides: Partial<AdminView>): AdminView {
  return {
    id: 'a-1',
    email: 'a@example.com',
    fullName: 'Ada Admin',
    status: 'ACTIVE',
    mfaEnabled: true,
    lastLoginAt: null,
    lockedUntil: null,
    roles: ['ANALYST'],
    ...overrides,
  }
}

const ADMINS = [
  admin({ id: OWNER.id, email: OWNER.email, fullName: 'Olivia Owner', roles: ['SUPER_ADMIN'] }),
  admin({ id: 'a-2', email: 'bob@example.com', fullName: 'Bob Builder', status: 'DISABLED' }),
  admin({ id: 'a-3', email: 'cara@example.com', fullName: 'Cara Locked', lockedUntil: new Date(Date.now() + 3_600_000).toISOString() }),
]

function baseHandlers() {
  return {
    'GET /api/admins': () => ({ body: { items: ADMINS, page: 0, size: 25, total: 3 } }),
    'GET /api/roles': () => ({ body: ROLES }),
    'GET /api/admins/invitations': () => ({
      body: [{ id: 'inv-1', email: 'pending@example.com', roles: ['ANALYST'], expiresAt: new Date(Date.now() + 3_600_000).toISOString(), createdAt: new Date().toISOString() }],
    }),
  }
}

function show(permissions = ['USER_MANAGE']) {
  return renderRoutes([{ path: '/', element: <AdminsPage /> }], { auth: fakeAuth({ permissions }) })
}

function row(name: string) {
  return screen.getByText(name).closest('tr')!
}

describe('AdminsPage', () => {
  it('lists the admins with their roles, status and 2FA state', async () => {
    mockFetch(baseHandlers())
    show()

    expect(await screen.findByText('Bob Builder')).toBeInTheDocument()
    expect(within(row('Bob Builder')).getByText('Disabled')).toBeInTheDocument()
    expect(within(row('Cara Locked')).getByText('Locked')).toBeInTheDocument()
    expect(within(row('Olivia Owner')).getByText('Active')).toBeInTheDocument()
    expect(within(row('Olivia Owner')).getByText('SUPER_ADMIN')).toBeInTheDocument()
    expect(screen.getByText('3 admins')).toBeInTheDocument()
    expect(screen.getByText('pending@example.com')).toBeInTheDocument()
  })

  it('offers only the actions that make sense for each admin', async () => {
    mockFetch(baseHandlers())
    show()
    await screen.findByText('Bob Builder')

    expect(within(row('Olivia Owner')).queryByRole('button', { name: 'Disable' })).not.toBeInTheDocument() // not yourself
    expect(within(row('Bob Builder')).getByRole('button', { name: 'Enable' })).toBeInTheDocument()
    expect(within(row('Bob Builder')).queryByRole('button', { name: 'Disable' })).not.toBeInTheDocument()
    expect(within(row('Cara Locked')).getByRole('button', { name: 'Unlock' })).toBeInTheDocument()
    expect(within(row('Cara Locked')).getByRole('button', { name: 'Disable' })).toBeInTheDocument()
  })

  it('names the tab, and calls ending someone\'s sessions "End sessions" so it is not mistaken for your own Sign out', async () => {
    const fake = mockFetch({ ...baseHandlers(), 'POST /api/admins/a-3/revoke-sessions': () => ({ status: 204 }) })
    show()
    await screen.findByText('Cara Locked')
    expect(document.title).toBe('Admins - Nexlyn BGV')
    expect(screen.queryByRole('button', { name: 'Sign out' })).not.toBeInTheDocument()

    await userEvent.click(within(row('Cara Locked')).getByRole('button', { name: 'End sessions' }))
    const dialog = screen.getByRole('dialog', { name: 'End all sessions of this admin?' })
    expect(dialog).toHaveTextContent('All their sessions end now')
    expect(fake.mock.calls.some(([url]) => String(url).endsWith('/revoke-sessions'))).toBe(false) // nothing until confirmed

    await userEvent.click(within(dialog).getByRole('button', { name: 'End sessions' }))
    await screen.findByText('Bob Builder')
    expect(fake.mock.calls.some(([url, init]) => url === '/api/admins/a-3/revoke-sessions' && init?.method === 'POST')).toBe(true)
  })

  it('hides the management buttons from an admin without the permission', async () => {
    mockFetch(baseHandlers())
    show([])
    await screen.findByText('Bob Builder')

    expect(screen.queryByRole('button', { name: 'Invite admin' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Enable' })).not.toBeInTheDocument()
  })

  it('asks before disabling, then calls the server and refreshes the list', async () => {
    const fake = mockFetch({ ...baseHandlers(), 'POST /api/admins/a-3/disable': () => ({ body: ADMINS[2] }) })
    show()
    await screen.findByText('Cara Locked')

    await userEvent.click(within(row('Cara Locked')).getByRole('button', { name: 'Disable' }))
    const dialog = screen.getByRole('dialog', { name: 'Disable this admin?' })
    expect(fake.mock.calls.some(([url]) => String(url).endsWith('/disable'))).toBe(false) // nothing yet

    await userEvent.click(within(dialog).getByRole('button', { name: 'Disable' }))

    await screen.findByText('Bob Builder')
    expect(fake.mock.calls.some(([url, init]) => url === '/api/admins/a-3/disable' && init?.method === 'POST')).toBe(true)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('shows the server\'s reason when an action is refused', async () => {
    mockFetch({
      ...baseHandlers(),
      'POST /api/admins/a-2/enable': () => ({ status: 409, body: { code: 'CONFLICT', message: 'Cannot do that right now.' } }),
    })
    show()
    await screen.findByText('Bob Builder')

    await userEvent.click(within(row('Bob Builder')).getByRole('button', { name: 'Enable' }))
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Enable' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Cannot do that right now.')
  })

  it('creates an invitation and shows the one-time link with a warning', async () => {
    const fake = mockFetch({
      ...baseHandlers(),
      'POST /api/admins/invitations': () => ({
        status: 201,
        body: { id: 'inv-2', email: 'new@example.com', expiresAt: new Date(Date.now() + 86_400_000).toISOString(), inviteToken: 'tok/en+1' },
      }),
    })
    show()
    await screen.findByText('Bob Builder')

    await userEvent.click(screen.getByRole('button', { name: 'Invite admin' }))
    const dialog = screen.getByRole('dialog', { name: 'Invite an admin' })
    const create = within(dialog).getByRole('button', { name: 'Create invitation' })
    expect(create).toBeDisabled() // no role chosen yet

    await userEvent.type(within(dialog).getByLabelText('Email (required)'), 'new@example.com')
    await userEvent.click(within(dialog).getByRole('checkbox', { name: /Analyst/ }))
    await userEvent.click(create)

    const done = await screen.findByRole('dialog', { name: 'Invitation created' })
    expect(within(done).getByRole('status')).toHaveTextContent('shown only now')
    const link = within(done).getByLabelText('Invitation link') as HTMLInputElement
    expect(link.value).toBe(`${window.location.origin}/accept-invite?token=tok%2Fen%2B1`)

    const post = fake.mock.calls.find(([url, init]) => url === '/api/admins/invitations' && init?.method === 'POST')!
    expect(JSON.parse(post[1]!.body as string)).toEqual({ email: 'new@example.com', roleIds: ['role-analyst'] })
  })

  it('checks the email before inviting', async () => {
    const fake = mockFetch(baseHandlers())
    show()
    await screen.findByText('Bob Builder')

    await userEvent.click(screen.getByRole('button', { name: 'Invite admin' }))
    const dialog = screen.getByRole('dialog')
    await userEvent.click(within(dialog).getByRole('checkbox', { name: /Analyst/ }))
    await userEvent.type(within(dialog).getByLabelText('Email (required)'), 'nope')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create invitation' }))

    expect(await within(dialog).findByText('Enter a valid email address')).toBeInTheDocument()
    expect(fake.mock.calls.some(([url, init]) => url === '/api/admins/invitations' && init?.method === 'POST')).toBe(false)
  })

  it('saves changed roles and warns that the admin will be signed out', async () => {
    const fake = mockFetch({ ...baseHandlers(), 'PUT /api/admins/a-3': () => ({ body: ADMINS[2] }) })
    show()
    await screen.findByText('Cara Locked')

    await userEvent.click(within(row('Cara Locked')).getByRole('button', { name: 'Edit' }))
    const dialog = screen.getByRole('dialog', { name: 'Edit cara@example.com' })
    expect(dialog).toHaveTextContent('signs this admin out everywhere')
    expect(within(dialog).getByRole('checkbox', { name: /Analyst/ })).toBeChecked()

    await userEvent.click(within(dialog).getByRole('checkbox', { name: /Super Admin/ }))
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }))

    await screen.findByText('Bob Builder')
    const put = fake.mock.calls.find(([url, init]) => url === '/api/admins/a-3' && init?.method === 'PUT')!
    expect(JSON.parse(put[1]!.body as string).roleIds).toEqual(['role-analyst', 'role-super'])
  })

  it('revokes a pending invitation', async () => {
    const fake = mockFetch({ ...baseHandlers(), 'DELETE /api/admins/invitations/inv-1': () => ({ status: 204 }) })
    show()
    await screen.findByText('pending@example.com')

    await userEvent.click(screen.getByRole('button', { name: 'Revoke' }))

    await screen.findByText('Bob Builder')
    expect(fake.mock.calls.some(([url, init]) => url === '/api/admins/invitations/inv-1' && init?.method === 'DELETE')).toBe(true)
  })

  it('reports a failure to load the list', async () => {
    mockFetch({ ...baseHandlers(), 'GET /api/admins': () => ({ status: 403, body: { code: 'FORBIDDEN', message: 'no' } }) })
    show()
    expect(await screen.findByRole('alert')).toHaveTextContent('You do not have permission to do that.')
  })
})
