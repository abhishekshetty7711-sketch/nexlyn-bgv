import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { fakeAuth, mockFetch, renderRoutes } from '@/test/testUtils'
import type { RoleView } from './api'
import { RolesPage } from './RolesPage'

const PERMISSIONS = [
  { code: 'CASE_CREATE', description: 'Create cases' },
  { code: 'CASE_READ_ALL', description: 'Read every case' },
  { code: 'USER_MANAGE', description: 'Manage admins' },
]

const ROLES: RoleView[] = [
  { id: 'r-analyst', code: 'ANALYST', name: 'Analyst', description: 'Prepares cases', systemRole: true, permissions: ['CASE_CREATE'], memberCount: 2 },
  { id: 'r-viewer', code: 'CASE_VIEWER', name: 'Case viewer', description: null, systemRole: false, permissions: ['CASE_READ_ALL'], memberCount: 0 },
  { id: 'r-busy', code: 'BUSY_ROLE', name: 'Busy role', description: null, systemRole: false, permissions: [], memberCount: 3 },
]

function handlers() {
  return {
    'GET /api/roles': () => ({ body: ROLES }),
    'GET /api/permissions': () => ({ body: PERMISSIONS }),
  }
}

function show(permissions = ['ROLE_MANAGE']) {
  return renderRoutes([{ path: '/', element: <RolesPage /> }], { auth: fakeAuth({ permissions }) })
}

function card(name: string) {
  return screen.getByRole('heading', { name }).closest('[data-slot="card"]') as HTMLElement
}

describe('RolesPage', () => {
  it('lists built-in and custom roles with their permissions and member counts', async () => {
    mockFetch(handlers())
    show()

    expect(await screen.findByRole('heading', { name: 'Analyst' })).toBeInTheDocument()
    expect(within(card('Analyst')).getByText('Built-in')).toBeInTheDocument()
    expect(within(card('Analyst')).getByText('2 admins')).toBeInTheDocument()
    expect(within(card('Case viewer')).getByText('Custom')).toBeInTheDocument()
    expect(within(card('Case viewer')).getByText('CASE_READ_ALL')).toBeInTheDocument()
  })

  it('offers deletion only for custom roles that nobody holds', async () => {
    mockFetch(handlers())
    show()
    await screen.findByRole('heading', { name: 'Analyst' })

    expect(within(card('Analyst')).queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument()
    expect(within(card('Busy role')).queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument()
    expect(within(card('Case viewer')).getByRole('button', { name: 'Delete' })).toBeInTheDocument()
  })

  it('hides the buttons from someone without ROLE_MANAGE', async () => {
    mockFetch(handlers())
    show([])
    await screen.findByRole('heading', { name: 'Analyst' })
    expect(screen.queryByRole('button', { name: 'New role' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
  })

  it('lets a built-in role be renamed but locks its permissions, and does not send them', async () => {
    const fake = mockFetch({ ...handlers(), 'PUT /api/roles/r-analyst': () => ({ body: ROLES[0] }) })
    show()
    await screen.findByRole('heading', { name: 'Analyst' })

    await userEvent.click(within(card('Analyst')).getByRole('button', { name: 'Edit' }))
    const dialog = screen.getByRole('dialog', { name: 'Edit ANALYST' })
    expect(dialog).toHaveTextContent('only the name and description can change')
    for (const box of within(dialog).getAllByRole('checkbox')) {
      expect(box).toBeDisabled()
    }

    const name = within(dialog).getByLabelText('Name')
    await userEvent.clear(name)
    await userEvent.type(name, 'Case analyst')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }))

    await screen.findByRole('heading', { name: 'Case viewer' })
    const put = fake.mock.calls.find(([url, init]) => url === '/api/roles/r-analyst' && init?.method === 'PUT')!
    const body = JSON.parse(put[1]!.body as string) as Record<string, unknown>
    expect(body.name).toBe('Case analyst')
    expect(body).not.toHaveProperty('permissions')
  })

  it('warns that changing a custom role signs out its holders, and sends the chosen permissions', async () => {
    const fake = mockFetch({ ...handlers(), 'PUT /api/roles/r-viewer': () => ({ body: ROLES[1] }) })
    show()
    await screen.findByRole('heading', { name: 'Case viewer' })

    await userEvent.click(within(card('Case viewer')).getByRole('button', { name: 'Edit' }))
    const dialog = screen.getByRole('dialog', { name: 'Edit CASE_VIEWER' })
    expect(dialog).toHaveTextContent('signs out everyone who holds this role')
    await userEvent.click(within(dialog).getByRole('checkbox', { name: /CASE_CREATE/ }))
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }))

    await screen.findByRole('heading', { name: 'Analyst' })
    const put = fake.mock.calls.find(([url, init]) => url === '/api/roles/r-viewer' && init?.method === 'PUT')!
    expect((JSON.parse(put[1]!.body as string) as { permissions: string[] }).permissions).toEqual(['CASE_READ_ALL', 'CASE_CREATE'])
  })

  it('validates the code of a new role before sending it', async () => {
    const fake = mockFetch(handlers())
    show()
    await screen.findByRole('heading', { name: 'Analyst' })

    await userEvent.click(screen.getByRole('button', { name: 'New role' }))
    const dialog = screen.getByRole('dialog', { name: 'New custom role' })
    await userEvent.type(within(dialog).getByLabelText('Code'), 'bad code')
    await userEvent.type(within(dialog).getByLabelText('Name'), 'Whatever')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create role' }))

    expect(await within(dialog).findByText(/Use 3-50 capital letters/)).toBeInTheDocument()
    expect(fake.mock.calls.some(([url, init]) => url === '/api/roles' && init?.method === 'POST')).toBe(false)
  })

  it('creates a custom role with the chosen permissions', async () => {
    const fake = mockFetch({ ...handlers(), 'POST /api/roles': () => ({ status: 201, body: ROLES[1] }) })
    show()
    await screen.findByRole('heading', { name: 'Analyst' })

    await userEvent.click(screen.getByRole('button', { name: 'New role' }))
    const dialog = screen.getByRole('dialog', { name: 'New custom role' })
    await userEvent.type(within(dialog).getByLabelText('Code'), 'CASE_CLERK')
    await userEvent.type(within(dialog).getByLabelText('Name'), 'Case clerk')
    await userEvent.click(within(dialog).getByRole('checkbox', { name: /CASE_CREATE/ }))
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create role' }))

    await screen.findByRole('heading', { name: 'Case viewer' })
    const post = fake.mock.calls.find(([url, init]) => url === '/api/roles' && init?.method === 'POST')!
    expect(JSON.parse(post[1]!.body as string)).toEqual({
      code: 'CASE_CLERK',
      name: 'Case clerk',
      description: '',
      permissions: ['CASE_CREATE'],
    })
  })

  it('shows the server\'s reason when creation is refused', async () => {
    mockFetch({ ...handlers(), 'POST /api/roles': () => ({ status: 403, body: { code: 'FORBIDDEN', message: 'You cannot grant more access than you have yourself.' } }) })
    show()
    await screen.findByRole('heading', { name: 'Analyst' })

    await userEvent.click(screen.getByRole('button', { name: 'New role' }))
    const dialog = screen.getByRole('dialog')
    await userEvent.type(within(dialog).getByLabelText('Code'), 'GREEDY')
    await userEvent.type(within(dialog).getByLabelText('Name'), 'Greedy')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create role' }))

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('You do not have permission to do that.')
  })

  it('confirms before deleting an unused custom role', async () => {
    const fake = mockFetch({ ...handlers(), 'DELETE /api/roles/r-viewer': () => ({ status: 204 }) })
    show()
    await screen.findByRole('heading', { name: 'Case viewer' })

    await userEvent.click(within(card('Case viewer')).getByRole('button', { name: 'Delete' }))
    const dialog = screen.getByRole('dialog', { name: 'Delete this role?' })
    expect(fake.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(false)
    await userEvent.click(within(dialog).getByRole('button', { name: 'Delete' }))

    await screen.findByRole('heading', { name: 'Analyst' })
    expect(fake.mock.calls.some(([url, init]) => url === '/api/roles/r-viewer' && init?.method === 'DELETE')).toBe(true)
  })
})
