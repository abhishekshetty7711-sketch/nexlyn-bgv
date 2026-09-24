import { describe, expect, it, vi } from 'vitest'
import { tokenStore } from '@/features/auth/tokenStore'
import { ApiError, apiFetch, configureAuthHooks } from './httpClient'

function json(status: number, body?: unknown, headers: Record<string, string> = {}) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  })
}

function authorizationOf(call: unknown[]): string | undefined {
  const init = call[1] as RequestInit
  return (init.headers as Record<string, string>).Authorization
}

describe('apiFetch', () => {
  it('sends the in-memory access token as a bearer token and always includes cookies', async () => {
    tokenStore.set('token-1')
    const fake = vi.fn().mockResolvedValue(json(200, { ok: true }))
    vi.stubGlobal('fetch', fake)

    await expect(apiFetch<{ ok: boolean }>('/me')).resolves.toEqual({ ok: true })

    expect(authorizationOf(fake.mock.calls[0]!)).toBe('Bearer token-1')
    expect((fake.mock.calls[0]![1] as RequestInit).credentials).toBe('include')
  })

  it('does not send a token on public calls, and never tries to refresh after their 401', async () => {
    tokenStore.set('token-1')
    const refresh = vi.fn()
    configureAuthHooks({ refresh, onSessionEnded: vi.fn() })
    const fake = vi.fn().mockResolvedValue(json(401, { code: 'INVALID_CREDENTIALS', message: 'Invalid email or password.' }))
    vi.stubGlobal('fetch', fake)

    await expect(apiFetch('/auth/login', { json: { email: 'a', password: 'b' }, auth: false })).rejects.toMatchObject({
      status: 401,
      code: 'INVALID_CREDENTIALS',
    })

    expect(authorizationOf(fake.mock.calls[0]!)).toBeUndefined()
    expect(refresh).not.toHaveBeenCalled()
  })

  it('renews an expired token once and retries the same request with the new token', async () => {
    tokenStore.set('old')
    const refresh = vi.fn().mockResolvedValue('fresh')
    const onSessionEnded = vi.fn()
    configureAuthHooks({ refresh, onSessionEnded })
    const fake = vi
      .fn()
      .mockResolvedValueOnce(json(401, { code: 'UNAUTHENTICATED', message: 'Authentication is required.' }))
      .mockResolvedValueOnce(json(200, { name: 'Olivia' }))
    vi.stubGlobal('fetch', fake)

    await expect(apiFetch<{ name: string }>('/me')).resolves.toEqual({ name: 'Olivia' })

    expect(refresh).toHaveBeenCalledTimes(1)
    expect(authorizationOf(fake.mock.calls[0]!)).toBe('Bearer old')
    expect(authorizationOf(fake.mock.calls[1]!)).toBe('Bearer fresh')
    expect(onSessionEnded).not.toHaveBeenCalled()
  })

  it('ends the session when the token cannot be renewed', async () => {
    tokenStore.set('old')
    const onSessionEnded = vi.fn()
    configureAuthHooks({ refresh: vi.fn().mockResolvedValue(null), onSessionEnded })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(401, { code: 'UNAUTHENTICATED', message: 'x' })))

    await expect(apiFetch('/me')).rejects.toBeInstanceOf(ApiError)
    expect(onSessionEnded).toHaveBeenCalledTimes(1)
  })

  it('ends the session when the retry with a fresh token is refused too, without looping', async () => {
    tokenStore.set('old')
    const refresh = vi.fn().mockResolvedValue('fresh')
    const onSessionEnded = vi.fn()
    configureAuthHooks({ refresh, onSessionEnded })
    const fake = vi.fn().mockResolvedValue(json(401, { code: 'UNAUTHENTICATED', message: 'x' }))
    vi.stubGlobal('fetch', fake)

    await expect(apiFetch('/me')).rejects.toMatchObject({ status: 401 })
    expect(fake).toHaveBeenCalledTimes(2)
    expect(refresh).toHaveBeenCalledTimes(1)
    expect(onSessionEnded).toHaveBeenCalledTimes(1)
  })

  it('does not treat a 403 as an expired session', async () => {
    tokenStore.set('token')
    const refresh = vi.fn()
    configureAuthHooks({ refresh, onSessionEnded: vi.fn() })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(403, { code: 'FORBIDDEN', message: 'no' })))

    await expect(apiFetch('/roles')).rejects.toMatchObject({ status: 403, code: 'FORBIDDEN' })
    expect(refresh).not.toHaveBeenCalled()
  })

  it('turns the standard error body into an ApiError with codes, field errors and retry time', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        json(423, { code: 'ACCOUNT_LOCKED', message: 'Locked.', fieldErrors: [{ field: 'email', message: 'is invalid' }] }, { 'Retry-After': '900' }),
      ),
    )

    const error = await apiFetch('/auth/login', { json: {}, auth: false }).catch((problem: unknown) => problem)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 423, code: 'ACCOUNT_LOCKED', message: 'Locked.', retryAfterSeconds: 900 })
    expect((error as ApiError).fieldErrors).toEqual([{ field: 'email', message: 'is invalid' }])
  })

  it('copes with an error that is not JSON', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('<html>Bad gateway</html>', { status: 502 })))
    await expect(apiFetch('/me', { auth: false })).rejects.toMatchObject({ status: 502, code: 'UNKNOWN' })
  })

  it('returns undefined for 204 and sends JSON bodies with the right method', async () => {
    const fake = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fake)

    await expect(apiFetch<void>('/me/password', { method: 'PUT', json: { a: 1 } })).resolves.toBeUndefined()

    const init = fake.mock.calls[0]![1] as RequestInit
    expect(init.method).toBe('PUT')
    expect(init.body).toBe('{"a":1}')
    expect((init.headers as Record<string, string>)['Content-Type']).toBe('application/json')
  })
})
