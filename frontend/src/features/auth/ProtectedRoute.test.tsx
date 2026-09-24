import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { fakeAuth, renderRoutes } from '@/test/testUtils'
import { Can } from './Can'
import { ProtectedRoute } from './ProtectedRoute'

const routes = [
  { path: '/login', element: <p>login page</p> },
  {
    element: <ProtectedRoute />,
    children: [
      { path: '/home', element: <p>home page</p> },
      {
        element: <ProtectedRoute permission="USER_MANAGE" />,
        children: [{ path: '/admins', element: <p>admins page</p> }],
      },
      {
        element: <ProtectedRoute permission={['AUDIT_READ', 'ROLE_MANAGE']} />,
        children: [{ path: '/either', element: <p>either page</p> }],
      },
    ],
  },
]

describe('ProtectedRoute', () => {
  it('sends a signed-out visitor to the login page', () => {
    renderRoutes(routes, { route: '/home', auth: fakeAuth({ signedIn: false }) })
    expect(screen.getByText('login page')).toBeInTheDocument()
    expect(screen.queryByText('home page')).not.toBeInTheDocument()
  })

  it('remembers where the visitor was going', () => {
    const { router } = renderRoutes(routes, { route: '/admins', auth: fakeAuth({ signedIn: false }) })
    expect((router.state.location.state as { from: { pathname: string } }).from.pathname).toBe('/admins')
  })

  it('shows a spinner while the session is being restored, never a flash of the login page', () => {
    renderRoutes(routes, { route: '/home', auth: fakeAuth({ state: { status: 'loading' } }) })
    expect(screen.getByRole('status', { name: 'Loading' })).toBeInTheDocument()
    expect(screen.queryByText('login page')).not.toBeInTheDocument()
  })

  it('lets a signed-in admin through when no permission is required', () => {
    renderRoutes(routes, { route: '/home', auth: fakeAuth() })
    expect(screen.getByText('home page')).toBeInTheDocument()
  })

  it('shows "Not allowed" when the permission is missing, and the page when it is held', () => {
    const { unmount } = renderRoutes(routes, { route: '/admins', auth: fakeAuth({ permissions: ['CASE_CREATE'] }) })
    expect(screen.getByRole('heading', { name: 'Not allowed' })).toBeInTheDocument()
    expect(screen.queryByText('admins page')).not.toBeInTheDocument()
    unmount()

    renderRoutes(routes, { route: '/admins', auth: fakeAuth({ permissions: ['USER_MANAGE'] }) })
    expect(screen.getByText('admins page')).toBeInTheDocument()
  })

  it('accepts any one of several permissions', () => {
    renderRoutes(routes, { route: '/either', auth: fakeAuth({ permissions: ['ROLE_MANAGE'] }) })
    expect(screen.getByText('either page')).toBeInTheDocument()
  })
})

describe('Can', () => {
  function show(permissions: string[]) {
    return renderRoutes(
      [
        {
          path: '/',
          element: (
            <Can permission="USER_MANAGE" fallback={<p>fallback</p>}>
              <button>disable admin</button>
            </Can>
          ),
        },
      ],
      { auth: fakeAuth({ permissions }) },
    )
  }

  it('shows its children only to admins who hold the permission', () => {
    show(['USER_MANAGE'])
    expect(screen.getByRole('button', { name: 'disable admin' })).toBeInTheDocument()
    expect(screen.queryByText('fallback')).not.toBeInTheDocument()
  })

  it('shows the fallback (or nothing) to everyone else', () => {
    show(['CASE_CREATE'])
    expect(screen.queryByRole('button', { name: 'disable admin' })).not.toBeInTheDocument()
    expect(screen.getByText('fallback')).toBeInTheDocument()
  })
})
