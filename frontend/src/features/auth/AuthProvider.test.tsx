import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClientProvider } from '@tanstack/react-query'
import { describe, expect, it, vi } from 'vitest'
import { apiFetch } from '@/api/httpClient'
import { mockFetch, newQueryClient, OWNER } from '@/test/testUtils'
import { useAuth } from './AuthContext'
import { AuthProvider } from './AuthProvider'
import { tokenStore } from './tokenStore'

function Probe() {
  const { state, hasPermission, signOut } = useAuth()
  return (
    <div>
      <p>status: {state.status}</p>
      {state.status === 'authenticated' && <p>hello {state.me.fullName}</p>}
      {state.status === 'anonymous' && <p>ended by: {String(state.endedBy)}</p>}
      <p>{hasPermission('USER_MANAGE') ? 'can manage users' : 'cannot manage users'}</p>
      <button onClick={() => void signOut()}>sign out</button>
    </div>
  )
}

function renderProvider() {
  return render(
    <QueryClientProvider client={newQueryClient()}>
      <AuthProvider>
        <Probe />
      </AuthProvider>
    </QueryClientProvider>,
  )
}

const ME = { ...OWNER, permissions: ['USER_MANAGE'] }
const TOKENS = { accessToken: 'access-1', tokenType: 'Bearer', expiresInSeconds: 900 }

describe('AuthProvider', () => {
  it('starts signed out, without calling the server, when there is no session cookie', async () => {
    const fake = mockFetch({})
    renderProvider()

    expect(await screen.findByText('status: anonymous')).toBeInTheDocument()
    expect(fake).not.toHaveBeenCalled()
  })

  it('restores the session after a page reload from the refresh cookie', async () => {
    document.cookie = 'csrf_token=csrf-1; path=/'
    const fake = mockFetch({
      'POST /api/auth/refresh': () => ({ body: TOKENS }),
      'GET /api/me': () => ({ body: ME }),
    })
    renderProvider()

    expect(await screen.findByText('hello Olivia Owner')).toBeInTheDocument()
    expect(screen.getByText('can manage users')).toBeInTheDocument()
    // The access token is used for the API but lives only in memory.
    expect(tokenStore.get()).toBe('access-1')
    const meCall = fake.mock.calls.find(([url]) => url === '/api/me')!
    expect((meCall[1]!.headers as Record<string, string>).Authorization).toBe('Bearer access-1')
    expect(localStorage.length).toBe(0)
    expect(sessionStorage.length).toBe(0)
    expect(document.cookie).not.toContain('access-1')
  })

  it('stays signed out when the session cannot be restored', async () => {
    document.cookie = 'csrf_token=csrf-1; path=/'
    mockFetch({ 'POST /api/auth/refresh': () => ({ status: 401, body: { code: 'INVALID_REFRESH_TOKEN', message: 'ended' } }) })
    renderProvider()

    expect(await screen.findByText('status: anonymous')).toBeInTheDocument()
    expect(tokenStore.get()).toBeNull()
  })

  it('signs out on the server with the csrf header, forgets the token, and clears cached data', async () => {
    document.cookie = 'csrf_token=csrf-1; path=/'
    const fake = mockFetch({
      'POST /api/auth/refresh': () => ({ body: TOKENS }),
      'GET /api/me': () => ({ body: ME }),
      'POST /api/auth/logout': () => ({ status: 204 }),
    })
    const client = newQueryClient()
    client.setQueryData(['admins'], [{ email: 'secret@example.com' }])
    render(
      <QueryClientProvider client={client}>
        <AuthProvider>
          <Probe />
        </AuthProvider>
      </QueryClientProvider>,
    )
    await screen.findByText('hello Olivia Owner')

    await userEvent.click(screen.getByRole('button', { name: 'sign out' }))

    expect(await screen.findByText('status: anonymous')).toBeInTheDocument()
    expect(screen.getByText('ended by: signed-out')).toBeInTheDocument()
    const logoutCall = fake.mock.calls.find(([url]) => url === '/api/auth/logout')!
    expect((logoutCall[1]!.headers as Record<string, string>)['X-CSRF-Token']).toBe('csrf-1')
    expect(tokenStore.get()).toBeNull()
    expect(client.getQueryData(['admins'])).toBeUndefined()
  })

  it('renews an expired access token quietly and retries the request that failed', async () => {
    document.cookie = 'csrf_token=csrf-1; path=/'
    let refreshes = 0
    mockFetch({
      'POST /api/auth/refresh': () => ({ body: { ...TOKENS, accessToken: `access-${++refreshes}` } }),
      'GET /api/me': (request) => ({ body: ME, status: request.headers.get('Authorization') === 'Bearer access-1' ? 200 : 200 }),
      'GET /api/things': (request) =>
        request.headers.get('Authorization') === 'Bearer access-2'
          ? { body: { ok: true } }
          : { status: 401, body: { code: 'UNAUTHENTICATED', message: 'expired' } },
    })
    renderProvider()
    await screen.findByText('hello Olivia Owner')

    await expect(apiFetch<{ ok: boolean }>('/things')).resolves.toEqual({ ok: true })
    expect(refreshes).toBe(2)
  })

  it('ends the session, and says why, when the token cannot be renewed', async () => {
    document.cookie = 'csrf_token=csrf-1; path=/'
    let refreshes = 0
    mockFetch({
      'POST /api/auth/refresh': () =>
        ++refreshes === 1 ? { body: TOKENS } : { status: 401, body: { code: 'INVALID_REFRESH_TOKEN', message: 'ended' } },
      'GET /api/me': () => ({ body: ME }),
      'GET /api/things': () => ({ status: 401, body: { code: 'UNAUTHENTICATED', message: 'expired' } }),
    })
    renderProvider()
    await screen.findByText('hello Olivia Owner')

    await expect(apiFetch('/things')).rejects.toMatchObject({ status: 401 })

    await waitFor(() => expect(screen.getByText('ended by: expired')).toBeInTheDocument())
    expect(tokenStore.get()).toBeNull()
  })

  it('signs out when another tab signs out', async () => {
    if (typeof BroadcastChannel === 'undefined') {
      return // this environment has no cross-tab messaging, so there is nothing to test
    }
    document.cookie = 'csrf_token=csrf-1; path=/'
    mockFetch({
      'POST /api/auth/refresh': () => ({ body: TOKENS }),
      'GET /api/me': () => ({ body: ME }),
    })
    renderProvider()
    await screen.findByText('hello Olivia Owner')

    const otherTab = new BroadcastChannel('nexlyn-bgv-auth')
    otherTab.postMessage({ type: 'logout' })
    otherTab.close()

    await waitFor(() => expect(screen.getByText('status: anonymous')).toBeInTheDocument())
    expect(vi.isMockFunction(fetch)).toBe(true)
  })
})
