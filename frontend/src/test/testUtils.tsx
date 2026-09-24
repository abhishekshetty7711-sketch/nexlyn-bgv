import { render } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { type RouteObject, RouterProvider, createMemoryRouter } from 'react-router-dom'
import { vi } from 'vitest'
import { AuthContext, type AuthContextValue } from '@/features/auth/AuthContext'
import type { Me } from '@/features/auth/types'

export const OWNER: Me = {
  id: 'me-1',
  email: 'owner@example.com',
  fullName: 'Olivia Owner',
  roles: ['SUPER_ADMIN'],
  permissions: [],
  mfaEnabled: true,
  lastLoginAt: null,
}

interface FakeAuthOptions extends Partial<AuthContextValue> {
  /** Permissions of the signed-in admin. */
  permissions?: string[]
  /** Set to false to simulate a signed-out visitor. */
  signedIn?: boolean
}

/** An auth context for components under test: no network, no timers. */
export function fakeAuth({ permissions = [], signedIn = true, ...overrides }: FakeAuthOptions = {}): AuthContextValue {
  const granted = new Set(permissions)
  return {
    state: signedIn
      ? { status: 'authenticated', me: { ...OWNER, permissions } }
      : { status: 'anonymous', endedBy: null },
    hasPermission: (permission) => granted.has(permission),
    hasAnyPermission: (wanted) => wanted.some((permission) => granted.has(permission)),
    completeSignIn: vi.fn().mockResolvedValue(undefined),
    signOut: vi.fn().mockResolvedValue(undefined),
    idleSecondsLeft: null,
    staySignedIn: vi.fn(),
    ...overrides,
  }
}

export function newQueryClient(): QueryClient {
  return new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
}

/** Renders routes in memory with an auth context and a fresh query client. */
export function renderRoutes(routes: RouteObject[], options: { route?: string; auth?: AuthContextValue } = {}) {
  const router = createMemoryRouter(routes, { initialEntries: [options.route ?? '/'] })
  const result = render(
    <QueryClientProvider client={newQueryClient()}>
      <AuthContext.Provider value={options.auth ?? fakeAuth()}>
        <RouterProvider router={router} />
      </AuthContext.Provider>
    </QueryClientProvider>,
  )
  return { ...result, router }
}

export interface FakeRequest {
  url: string
  method: string
  headers: Headers
  body: unknown
}

export interface FakeResponse {
  status?: number
  body?: unknown
  headers?: Record<string, string>
}

export type FakeHandler = (request: FakeRequest) => FakeResponse

/**
 * Replaces `fetch` with a fake that answers by "METHOD /path" (query string ignored unless the
 * handler key includes it). Unknown calls answer 404. Returns the mock so tests can inspect calls.
 */
export function mockFetch(handlers: Record<string, FakeHandler>) {
  const fake = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    const method = (init?.method ?? 'GET').toUpperCase()
    const path = url.split('?')[0] ?? url
    const handler = handlers[`${method} ${url}`] ?? handlers[`${method} ${path}`]
    if (!handler) {
      return new Response(JSON.stringify({ code: 'NOT_FOUND', message: `No fake for ${method} ${url}` }), {
        status: 404,
        headers: { 'Content-Type': 'application/json' },
      })
    }
    const answer = handler({
      url,
      method,
      headers: new Headers(init?.headers),
      body: typeof init?.body === 'string' ? (JSON.parse(init.body) as unknown) : undefined,
    })
    const status = answer.status ?? 200
    return new Response(status === 204 || answer.body === undefined ? null : JSON.stringify(answer.body), {
      status,
      headers: { 'Content-Type': 'application/json', ...answer.headers },
    })
  })
  vi.stubGlobal('fetch', fake)
  return fake
}
