import { apiFetch } from '@/api/httpClient'
import { CSRF_HEADER, readCsrfToken } from './session'
import type { Challenge, Me, TokenResponse, TwoFactorSetupInfo } from './types'

/** Every call to `/api/auth/**` and `/api/me`. Public steps pass `auth: false` (no bearer token). */
export const authApi = {
  login: (email: string, password: string) =>
    apiFetch<Challenge>('/auth/login', { json: { email, password }, auth: false }),

  setupTwoFactor: (challengeToken: string) =>
    apiFetch<TwoFactorSetupInfo>('/auth/2fa/setup', { json: { challengeToken }, auth: false }),

  confirmTwoFactor: (challengeToken: string, code: string) =>
    apiFetch<TokenResponse>('/auth/2fa/confirm', { json: { challengeToken, code }, auth: false }),

  verifyTwoFactor: (challengeToken: string, code: string) =>
    apiFetch<TokenResponse>('/auth/2fa/verify', { json: { challengeToken, code }, auth: false }),

  acceptInvitation: (inviteToken: string, fullName: string, password: string) =>
    apiFetch<Challenge>('/auth/invitations/accept', {
      json: { inviteToken, fullName, password },
      auth: false,
    }),

  logout: () => {
    const csrf = readCsrfToken()
    return apiFetch<void>('/auth/logout', {
      method: 'POST',
      auth: false,
      headers: csrf ? { [CSRF_HEADER]: csrf } : {},
    })
  },

  fetchMe: () => apiFetch<Me>('/me'),

  changePassword: (currentPassword: string, newPassword: string) =>
    apiFetch<void>('/me/password', { method: 'PUT', json: { currentPassword, newPassword } }),
}
