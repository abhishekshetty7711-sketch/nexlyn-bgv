import { ApiError } from './httpClient'

/** Text to show for a failed admin operation. The server's messages are written to be shown to people. */
export function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 403) {
      return 'You do not have permission to do that.'
    }
    if (error.status >= 500) {
      return 'The server had a problem. Please try again in a moment.'
    }
    const detail = error.fieldErrors.map((field) => `${field.field} ${field.message}`).join('; ')
    return detail ? `${error.message} (${detail})` : error.message
  }
  return 'Something went wrong. Check your connection and try again.'
}
