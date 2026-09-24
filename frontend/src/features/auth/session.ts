import { API_BASE_URL } from '@/api/httpClient'
import { tokenStore } from './tokenStore'
import type { TokenResponse } from './types'

const CSRF_COOKIE = 'csrf_token'
export const CSRF_HEADER = 'X-CSRF-Token'

/**
 * The server sets a readable `csrf_token` cookie at login. The page copies it into the
 * `X-CSRF-Token` header on the cookie-based endpoints (double-submit): a site that merely causes the
 * browser to send cookies cannot read it, so it cannot add the header.
 */
export function readCsrfToken(): string | null {
  for (const part of document.cookie.split(';')) {
    const [name, ...rest] = part.trim().split('=')
    if (name === CSRF_COOKIE) {
      return decodeURIComponent(rest.join('='))
    }
  }
  return null
}

let inFlight: Promise<TokenResponse | null> | null = null

/**
 * Exchanges the httpOnly refresh cookie for a new access token (the server rotates the cookie).
 * Callers that overlap (React StrictMode, several requests failing at once) share one request, so a
 * rotated refresh token is never presented twice.
 */
export function refreshSession(): Promise<TokenResponse | null> {
  inFlight ??= doRefresh().finally(() => {
    inFlight = null
  })
  return inFlight
}

async function doRefresh(): Promise<TokenResponse | null> {
  const csrf = readCsrfToken()
  if (!csrf) {
    return null // never signed in (or signed out): there is no session to renew
  }
  try {
    const response = await fetch(`${API_BASE_URL}/auth/refresh`, {
      method: 'POST',
      credentials: 'include',
      headers: { [CSRF_HEADER]: csrf },
    })
    if (!response.ok) {
      tokenStore.set(null)
      return null
    }
    const tokens = (await response.json()) as TokenResponse
    tokenStore.set(tokens.accessToken)
    return tokens
  } catch {
    return null // network error: treat as "cannot renew", the caller decides what to do
  }
}
