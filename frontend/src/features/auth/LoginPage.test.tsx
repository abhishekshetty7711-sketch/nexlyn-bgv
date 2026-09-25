import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { fakeAuth, mockFetch, renderRoutes } from '@/test/testUtils'
import { LoginPage } from './LoginPage'

const routes = [
  { path: '/login', element: <LoginPage /> },
  { path: '/', element: <p>dashboard</p> },
]

function signedOut(overrides = {}) {
  return fakeAuth({ signedIn: false, ...overrides })
}

async function typeCredentials(email = 'owner@example.com', password = 'Tr1cky-Orange-Kettle') {
  await userEvent.type(screen.getByLabelText('Email'), email)
  await userEvent.type(screen.getByLabelText('Password'), password)
  await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
}

describe('LoginPage', () => {
  it('shows the brand, says which step this is, and moves the focus to the first field', async () => {
    mockFetch({ 'POST /api/auth/login': () => ({ body: { status: '2FA_REQUIRED', challengeToken: 'challenge-1', expiresInSeconds: 300 } }) })
    renderRoutes(routes, { route: '/login', auth: signedOut() })

    expect(screen.getByRole('heading', { name: 'Nexlyn BGV' })).toBeInTheDocument()
    expect(screen.getByText('Sign in to the admin console')).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toHaveFocus()

    await typeCredentials()
    expect(await screen.findByText('Two-step verification')).toBeInTheDocument()
    expect(screen.getByLabelText('6-digit code')).toHaveFocus()
  })

  it('names the tab after the step and has one main landmark', async () => {
    mockFetch({ 'POST /api/auth/login': () => ({ body: { status: '2FA_REQUIRED', challengeToken: 'challenge-1', expiresInSeconds: 300 } }) })
    renderRoutes(routes, { route: '/login', auth: signedOut() })

    expect(document.title).toBe('Sign in - Nexlyn BGV')
    expect(screen.getAllByRole('main')).toHaveLength(1)

    await typeCredentials()
    await screen.findByText('Two-step verification')
    expect(document.title).toBe('Two-step verification - Nexlyn BGV')
  })

  it('checks the form before calling the server', async () => {
    const fake = mockFetch({})
    renderRoutes(routes, { route: '/login', auth: signedOut() })

    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByText('Enter your email address')).toBeInTheDocument()
    expect(screen.getByText('Enter your password')).toBeInTheDocument()
    expect(fake).not.toHaveBeenCalled()
  })

  it('answers a wrong password with one generic message and clears the password field', async () => {
    mockFetch({ 'POST /api/auth/login': () => ({ status: 401, body: { code: 'INVALID_CREDENTIALS', message: 'Invalid email or password.' } }) })
    renderRoutes(routes, { route: '/login', auth: signedOut() })

    await typeCredentials()

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password.')
    expect(screen.getByLabelText('Password')).toHaveValue('')
    expect(screen.getByLabelText('Email')).toHaveValue('owner@example.com')
  })

  it('tells a locked-out admin how long to wait', async () => {
    mockFetch({
      'POST /api/auth/login': () => ({
        status: 423,
        body: { code: 'ACCOUNT_LOCKED', message: 'locked' },
        headers: { 'Retry-After': '900' },
      }),
    })
    renderRoutes(routes, { route: '/login', auth: signedOut() })

    await typeCredentials()

    expect(await screen.findByRole('alert')).toHaveTextContent('temporarily locked. Try again in about 15 minutes')
  })

  it('asks for the authenticator code, signs in with it, and offers a backup code instead', async () => {
    const fake = mockFetch({
      'POST /api/auth/login': () => ({ body: { status: '2FA_REQUIRED', challengeToken: 'challenge-1', expiresInSeconds: 300 } }),
      'POST /api/auth/2fa/verify': () => ({ body: { accessToken: 'access-1', tokenType: 'Bearer', expiresInSeconds: 900 } }),
    })
    const completeSignIn = vi.fn().mockResolvedValue(undefined)
    renderRoutes(routes, { route: '/login', auth: signedOut({ completeSignIn }) })

    await typeCredentials()
    expect(await screen.findByText('Enter the 6-digit code from your authenticator app.')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Use a backup code instead' }))
    expect(screen.getByLabelText('Backup code')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Use my authenticator app instead' }))

    await userEvent.type(screen.getByLabelText('6-digit code'), '123456')
    await userEvent.click(screen.getByRole('button', { name: 'Verify' }))

    await waitFor(() => expect(completeSignIn).toHaveBeenCalledWith(expect.objectContaining({ accessToken: 'access-1' })))
    const verify = fake.mock.calls.find(([url]) => url === '/api/auth/2fa/verify')!
    expect(JSON.parse(verify[1]!.body as string)).toEqual({ challengeToken: 'challenge-1', code: '123456' })
  })

  it('shows an error for a wrong code and lets the admin try again', async () => {
    mockFetch({
      'POST /api/auth/login': () => ({ body: { status: '2FA_REQUIRED', challengeToken: 'challenge-1', expiresInSeconds: 300 } }),
      'POST /api/auth/2fa/verify': () => ({ status: 401, body: { code: 'INVALID_CODE', message: 'no' } }),
    })
    renderRoutes(routes, { route: '/login', auth: signedOut() })

    await typeCredentials()
    await userEvent.type(await screen.findByLabelText('6-digit code'), '000000')
    await userEvent.click(screen.getByRole('button', { name: 'Verify' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('That code is not correct')
    expect(screen.getByLabelText('6-digit code')).toHaveValue('')
  })

  it('starts over with an explanation when the sign-in step has expired', async () => {
    mockFetch({
      'POST /api/auth/login': () => ({ body: { status: '2FA_REQUIRED', challengeToken: 'challenge-1', expiresInSeconds: 300 } }),
      'POST /api/auth/2fa/verify': () => ({ status: 401, body: { code: 'INVALID_CHALLENGE', message: 'expired' } }),
    })
    renderRoutes(routes, { route: '/login', auth: signedOut() })

    await typeCredentials()
    await userEvent.type(await screen.findByLabelText('6-digit code'), '123456')
    await userEvent.click(screen.getByRole('button', { name: 'Verify' }))

    expect(await screen.findByText('Your sign-in step expired. Please sign in again.')).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toBeInTheDocument()
  })

  it('walks a new admin through two-factor setup: scan, confirm, then save the backup codes', async () => {
    const fake = mockFetch({
      'POST /api/auth/login': () => ({ body: { status: '2FA_SETUP_REQUIRED', challengeToken: 'challenge-1', expiresInSeconds: 300 } }),
      'POST /api/auth/2fa/setup': () => ({
        body: { secret: 'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP', otpauthUri: 'otpauth://totp/Nexlyn%20BGV:owner%40example.com?secret=JBSWY3DP' },
      }),
      'POST /api/auth/2fa/confirm': () => ({
        body: { accessToken: 'access-1', tokenType: 'Bearer', expiresInSeconds: 900, backupCodes: ['AAAAA-BBBBB', 'CCCCC-DDDDD'] },
      }),
    })
    const completeSignIn = vi.fn().mockResolvedValue(undefined)
    renderRoutes(routes, { route: '/login', auth: signedOut({ completeSignIn }) })

    await typeCredentials()

    expect(await screen.findByRole('heading', { name: 'Set up two-factor authentication' })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'QR code for your authenticator app' })).toBeInTheDocument()
    expect(screen.getByText('JBSW Y3DP EHPK 3PXP JBSW Y3DP EHPK 3PXP')).toBeInTheDocument()
    expect(fake.mock.calls.filter(([url]) => url === '/api/auth/2fa/setup')).toHaveLength(1) // asked only once

    await userEvent.type(screen.getByLabelText('6-digit code'), '123456')
    await userEvent.click(screen.getByRole('button', { name: 'Verify and continue' }))

    // Backup codes are shown, and nothing is signed in until they are acknowledged.
    expect(await screen.findByRole('heading', { name: 'Save your backup codes' })).toBeInTheDocument()
    expect(screen.getByText('AAAAA-BBBBB')).toBeInTheDocument()
    expect(screen.getByText('CCCCC-DDDDD')).toBeInTheDocument()
    const proceed = screen.getByRole('button', { name: 'Continue' })
    expect(proceed).toBeDisabled()
    expect(completeSignIn).not.toHaveBeenCalled()

    await userEvent.click(screen.getByRole('checkbox', { name: /I have saved these codes/ }))
    await userEvent.click(proceed)
    expect(completeSignIn).toHaveBeenCalledWith(expect.objectContaining({ accessToken: 'access-1' }))
  })

  it('explains why the previous session ended', () => {
    renderRoutes(routes, {
      route: '/login',
      auth: fakeAuth({ state: { status: 'anonymous', endedBy: 'idle' } }),
    })
    expect(screen.getByText('You were signed out because you were inactive.')).toBeInTheDocument()
  })

  it('sends an already signed-in admin on to where they were going', () => {
    renderRoutes(routes, { route: '/login', auth: fakeAuth() })
    expect(screen.getByText('dashboard')).toBeInTheDocument()
  })
})
