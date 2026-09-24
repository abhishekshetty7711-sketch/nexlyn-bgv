import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { fakeAuth, mockFetch, renderRoutes } from '@/test/testUtils'
import { AcceptInvitationPage } from './AcceptInvitationPage'
import { ChangePasswordPage } from './ChangePasswordPage'

const GOOD = 'Tr1cky-Orange-Kettle'

describe('AcceptInvitationPage', () => {
  const routes = [{ path: '/accept-invite', element: <AcceptInvitationPage /> }, { path: '/', element: <p>dashboard</p> }]

  it('needs the link from the invitation', () => {
    renderRoutes(routes, { route: '/accept-invite', auth: fakeAuth({ signedIn: false }) })
    expect(screen.getByRole('alert')).toHaveTextContent('needs the link from your invitation')
  })

  it('removes the token from the address bar as soon as it has been read', async () => {
    mockFetch({})
    const { router } = renderRoutes(routes, { route: '/accept-invite?token=secret-token', auth: fakeAuth({ signedIn: false }) })

    await screen.findByLabelText('Full name')
    expect(router.state.location.search).toBe('')
    expect(router.state.location.pathname).toBe('/accept-invite')
  })

  it('checks the password rules and the confirmation before sending anything', async () => {
    const fake = mockFetch({})
    renderRoutes(routes, { route: '/accept-invite?token=secret-token', auth: fakeAuth({ signedIn: false }) })

    await userEvent.type(await screen.findByLabelText('Full name'), 'Ada')
    await userEvent.type(screen.getByLabelText('Password'), 'short')
    await userEvent.type(screen.getByLabelText('Confirm password'), 'different')
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }))

    expect(await screen.findByText('Use at least 12 characters')).toBeInTheDocument()
    expect(screen.getByText('The two passwords do not match')).toBeInTheDocument()
    expect(fake).not.toHaveBeenCalled()
  })

  it('sends the token, name and password, then continues with two-factor setup', async () => {
    const fake = mockFetch({
      'POST /api/auth/invitations/accept': () => ({ body: { status: '2FA_SETUP_REQUIRED', challengeToken: 'challenge-1', expiresInSeconds: 300 } }),
      'POST /api/auth/2fa/setup': () => ({ body: { secret: 'JBSWY3DPEHPK3PXP', otpauthUri: 'otpauth://totp/x?secret=JBSWY3DPEHPK3PXP' } }),
    })
    renderRoutes(routes, { route: '/accept-invite?token=secret-token', auth: fakeAuth({ signedIn: false }) })

    await userEvent.type(await screen.findByLabelText('Full name'), 'Ada Lovelace')
    await userEvent.type(screen.getByLabelText('Password'), GOOD)
    await userEvent.type(screen.getByLabelText('Confirm password'), GOOD)
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }))

    expect(await screen.findByRole('heading', { name: 'Set up two-factor authentication' })).toBeInTheDocument()
    const accept = fake.mock.calls.find(([url]) => url === '/api/auth/invitations/accept')!
    expect(JSON.parse(accept[1]!.body as string)).toEqual({ inviteToken: 'secret-token', fullName: 'Ada Lovelace', password: GOOD })
  })

  it('explains an invalid or expired link', async () => {
    mockFetch({ 'POST /api/auth/invitations/accept': () => ({ status: 400, body: { code: 'INVALID_INVITATION', message: 'x' } }) })
    renderRoutes(routes, { route: '/accept-invite?token=old', auth: fakeAuth({ signedIn: false }) })

    await userEvent.type(await screen.findByLabelText('Full name'), 'Ada')
    await userEvent.type(screen.getByLabelText('Password'), GOOD)
    await userEvent.type(screen.getByLabelText('Confirm password'), GOOD)
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('invitation link is invalid or has expired')
  })
})

describe('ChangePasswordPage', () => {
  const routes = [{ path: '/', element: <ChangePasswordPage /> }]

  async function fill(current: string, next: string, confirm = next) {
    await userEvent.type(screen.getByLabelText('Current password'), current)
    await userEvent.type(screen.getByLabelText('New password'), next)
    await userEvent.type(screen.getByLabelText('Confirm new password'), confirm)
    await userEvent.click(screen.getByRole('button', { name: 'Change password' }))
  }

  it('warns that every session ends', () => {
    mockFetch({})
    renderRoutes(routes)
    expect(screen.getByText(/signed out everywhere/)).toBeInTheDocument()
  })

  it('refuses a new password equal to the current one without calling the server', async () => {
    const fake = mockFetch({})
    renderRoutes(routes)
    await fill(GOOD, GOOD)
    expect(await screen.findByText('Choose a password different from your current one')).toBeInTheDocument()
    expect(fake).not.toHaveBeenCalled()
  })

  it('changes the password and then signs out, saying why', async () => {
    const fake = mockFetch({ 'PUT /api/me/password': () => ({ status: 204 }) })
    const signOut = vi.fn().mockResolvedValue(undefined)
    renderRoutes(routes, { auth: fakeAuth({ signOut }) })

    await fill(GOOD, 'Blue-Whale-Sings-42')

    await vi.waitFor(() => expect(signOut).toHaveBeenCalledWith('password-changed'))
    const put = fake.mock.calls.find(([url]) => url === '/api/me/password')!
    expect(JSON.parse(put[1]!.body as string)).toEqual({ currentPassword: GOOD, newPassword: 'Blue-Whale-Sings-42' })
  })

  it('reports a wrong current password and stays signed in', async () => {
    mockFetch({ 'PUT /api/me/password': () => ({ status: 401, body: { code: 'INVALID_CREDENTIALS', message: 'The current password is not correct.' } }) })
    const signOut = vi.fn()
    renderRoutes(routes, { auth: fakeAuth({ signOut }) })

    await fill('Wrong-Password-1!', 'Blue-Whale-Sings-42')

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password.')
    expect(signOut).not.toHaveBeenCalled()
    expect(screen.getByLabelText('Current password')).toHaveValue('')
  })
})
