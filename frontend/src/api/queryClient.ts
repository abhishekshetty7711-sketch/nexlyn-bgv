import { QueryClient } from '@tanstack/react-query'
import { ApiError } from './httpClient'

/** Wait this long before the single retry, so a failed screen shows its error within about two seconds. */
export const RETRY_DELAY_MS = 500

/**
 * A failed read is tried once more, and only if trying again could help: a network error or a 5xx answer.
 * A 4xx answer (not allowed, not found, invalid) will fail the same way, so it is shown straight away.
 */
export function shouldRetry(failureCount: number, error: unknown): boolean {
  if (error instanceof ApiError && error.status < 500) {
    return false
  }
  return failureCount < 1
}

export function createQueryClient(): QueryClient {
  return new QueryClient({ defaultOptions: { queries: { retry: shouldRetry, retryDelay: RETRY_DELAY_MS } } })
}
