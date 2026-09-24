import { tokenStore } from '@/features/auth/tokenStore'

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api'

/** One field-level problem reported by the server (never contains the rejected value). */
export interface FieldError {
  field: string
  message: string
}

/** Standard error body of the API: `{ code, message, fieldErrors, correlationId }`. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: FieldError[]
  readonly retryAfterSeconds: number | null

  constructor(
    status: number,
    message: string,
    code = 'UNKNOWN',
    fieldErrors: FieldError[] = [],
    retryAfterSeconds: number | null = null,
  ) {
    super(message)
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
    this.retryAfterSeconds = retryAfterSeconds
  }
}

interface ErrorBody {
  code?: string
  message?: string
  fieldErrors?: FieldError[]
}

/**
 * Hooks the auth layer registers so the client can renew an expired access token and react when
 * the session is over. Kept as hooks (not imports) so this file has no dependency on React.
 */
export interface AuthHooks {
  /** Returns a fresh access token, or null if the session cannot be renewed. */
  refresh: () => Promise<string | null>
  /** Called once when a request proves the session is over. */
  onSessionEnded: () => void
}

let authHooks: AuthHooks | null = null

export function configureAuthHooks(hooks: AuthHooks | null): void {
  authHooks = hooks
}

export interface RequestOptions {
  method?: string
  /** Sent as the JSON body. */
  json?: unknown
  headers?: Record<string, string>
  /** Sent as a multipart body (file uploads). The browser sets the content type and boundary. */
  form?: FormData
  /** How to read a successful answer: JSON (default) or the raw bytes (files). */
  as?: 'json' | 'blob'
  /** Public endpoints (login, ...) pass `false`: no bearer token, and no refresh-and-retry on 401. */
  auth?: boolean
}

async function send(path: string, options: RequestOptions, token: string | null): Promise<Response> {
  const headers: Record<string, string> = { ...options.headers }
  if (options.json !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  return fetch(`${API_BASE_URL}${path}`, {
    method: options.method ?? (options.json !== undefined || options.form !== undefined ? 'POST' : 'GET'),
    credentials: 'include',
    headers,
    body: options.form ?? (options.json !== undefined ? JSON.stringify(options.json) : undefined),
  })
}

async function toApiError(response: Response, path: string): Promise<ApiError> {
  let body: ErrorBody | null = null
  try {
    body = (await response.json()) as ErrorBody
  } catch {
    // Not JSON (for example a proxy error page): fall back to a generic message.
  }
  const retryAfter = Number(response.headers.get('Retry-After'))
  return new ApiError(
    response.status,
    body?.message ?? `Request to ${path} failed with ${response.status}`,
    body?.code ?? 'UNKNOWN',
    body?.fieldErrors ?? [],
    Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter : null,
  )
}

export async function apiFetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const authenticated = options.auth !== false
  let response = await send(path, options, authenticated ? tokenStore.get() : null)

  // An expired access token: renew it once, then retry the same request once.
  if (response.status === 401 && authenticated && authHooks) {
    const fresh = await authHooks.refresh()
    if (fresh) {
      response = await send(path, options, fresh)
    }
    if (response.status === 401 || !fresh) {
      authHooks.onSessionEnded()
    }
  }

  if (!response.ok) {
    throw await toApiError(response, path)
  }
  if (response.status === 204) {
    return undefined as T
  }
  if (options.as === 'blob') {
    return (await response.blob()) as T
  }
  return (await response.json()) as T
}
