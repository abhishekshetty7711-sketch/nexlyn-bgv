import { ApiError } from '@/api/httpClient'

function waitText(seconds: number | null): string {
  if (seconds === null) {
    return 'later'
  }
  if (seconds < 90) {
    return `in about ${seconds} seconds`
  }
  const minutes = Math.ceil(seconds / 60)
  return minutes < 90 ? `in about ${minutes} minutes` : `in about ${Math.ceil(minutes / 60)} hours`
}

/**
 * Plain-language text for a failed auth call. Deliberately generic where the server is (a wrong
 * password, an unknown email and a disabled account all read the same).
 */
export function describeAuthError(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return 'Something went wrong. Check your connection and try again.'
  }
  switch (error.code) {
    case 'INVALID_CREDENTIALS':
      return 'Invalid email or password.'
    case 'ACCOUNT_LOCKED':
      return `This account is temporarily locked. Try again ${waitText(error.retryAfterSeconds)}.`
    case 'RATE_LIMITED':
      return `Too many attempts. Please try again ${waitText(error.retryAfterSeconds)}.`
    case 'INVALID_CODE':
      return 'That code is not correct. Check your authenticator app and try again.'
    case 'INVALID_CHALLENGE':
      return 'Your sign-in step expired. Please sign in again.'
    case 'WEAK_PASSWORD':
      return 'That password does not meet the rules. Use at least 12 characters and avoid common passwords.'
    case 'INVALID_INVITATION':
      return 'This invitation link is invalid or has expired. Ask an administrator for a new one.'
    default:
      return error.status >= 500 ? 'The server had a problem. Please try again in a moment.' : error.message
  }
}

/** True when the sign-in step has to start over (the short-lived challenge is gone). */
export function isChallengeExpired(error: unknown): boolean {
  return error instanceof ApiError && error.code === 'INVALID_CHALLENGE'
}
