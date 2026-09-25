import { describe, expect, it } from 'vitest'
import { ApiError } from './httpClient'
import { createQueryClient, RETRY_DELAY_MS, shouldRetry } from './queryClient'

describe('shouldRetry', () => {
  it.each([400, 401, 403, 404, 409, 429])('never retries a %i answer, it would fail the same way', (status) => {
    expect(shouldRetry(0, new ApiError(status, 'no'))).toBe(false)
  })

  it('retries a server error or a network error once', () => {
    expect(shouldRetry(0, new ApiError(500, 'boom'))).toBe(true)
    expect(shouldRetry(0, new ApiError(503, 'busy'))).toBe(true)
    expect(shouldRetry(0, new TypeError('Failed to fetch'))).toBe(true)
    expect(shouldRetry(1, new ApiError(500, 'boom'))).toBe(false)
    expect(shouldRetry(1, new TypeError('Failed to fetch'))).toBe(false)
  })

  it('is what the app-wide query client uses, with a short wait, so an error shows within about two seconds', () => {
    const options = createQueryClient().getDefaultOptions().queries
    expect(options?.retry).toBe(shouldRetry)
    expect(options?.retryDelay).toBe(RETRY_DELAY_MS)
    expect(RETRY_DELAY_MS).toBeLessThanOrEqual(1000)
  })
})
