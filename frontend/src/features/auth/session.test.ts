import { describe, expect, it, vi } from 'vitest'
import { readCsrfToken, refreshSession } from './session'
import { tokenStore } from './tokenStore'

function tokens(accessToken: string) {
  return new Response(JSON.stringify({ accessToken, tokenType: 'Bearer', expiresInSeconds: 900 }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('readCsrfToken', () => {
  it('reads the readable csrf cookie', () => {
    document.cookie = 'other=1; path=/'
    document.cookie = 'csrf_token=abc%2Fdef; path=/'
    expect(readCsrfToken()).toBe('abc/def')
  })

  it('returns null when there is no csrf cookie', () => {
    expect(readCsrfToken()).toBeNull()
  })
})

describe('refreshSession', () => {
  it('does nothing when nobody is signed in (no csrf cookie means no session)', async () => {
    const fake = vi.fn()
    vi.stubGlobal('fetch', fake)

    await expect(refreshSession()).resolves.toBeNull()
    expect(fake).not.toHaveBeenCalled()
  })

  it('sends the csrf header with the cookie, and keeps the new token in memory only', async () => {
    document.cookie = 'csrf_token=csrf-1; path=/'
    const fake = vi.fn().mockResolvedValue(tokens('access-2'))
    vi.stubGlobal('fetch', fake)

    const result = await refreshSession()

    expect(result?.accessToken).toBe('access-2')
    expect(tokenStore.get()).toBe('access-2')
    const [url, init] = fake.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/auth/refresh')
    expect(init.method).toBe('POST')
    expect(init.credentials).toBe('include')
    expect((init.headers as Record<string, string>)['X-CSRF-Token']).toBe('csrf-1')
    expect(localStorage.length).toBe(0)
    expect(sessionStorage.length).toBe(0)
  })

  it('shares one request between callers that overlap, so a rotated token is never sent twice', async () => {
    document.cookie = 'csrf_token=csrf-1; path=/'
    const fake = vi.fn().mockResolvedValue(tokens('access-2'))
    vi.stubGlobal('fetch', fake)

    const [a, b, c] = await Promise.all([refreshSession(), refreshSession(), refreshSession()])

    expect(fake).toHaveBeenCalledTimes(1)
    expect(a).toBe(b)
    expect(b).toBe(c)
  })

  it('returns null and forgets the token when the server refuses', async () => {
    document.cookie = 'csrf_token=csrf-1; path=/'
    tokenStore.set('stale')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{}', { status: 401 })))

    await expect(refreshSession()).resolves.toBeNull()
    expect(tokenStore.get()).toBeNull()
  })

  it('returns null on a network error', async () => {
    document.cookie = 'csrf_token=csrf-1; path=/'
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')))
    await expect(refreshSession()).resolves.toBeNull()
  })

  it('can be used again after a request has finished', async () => {
    document.cookie = 'csrf_token=csrf-1; path=/'
    const fake = vi.fn().mockResolvedValueOnce(tokens('one')).mockResolvedValueOnce(tokens('two'))
    vi.stubGlobal('fetch', fake)

    expect((await refreshSession())?.accessToken).toBe('one')
    expect((await refreshSession())?.accessToken).toBe('two')
    expect(fake).toHaveBeenCalledTimes(2)
  })
})
