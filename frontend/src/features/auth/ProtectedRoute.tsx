import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { FullPageSpinner } from '@/components/ui/spinner'
import { useAuth } from './AuthContext'
import { ForbiddenPage } from './ForbiddenPage'

interface ProtectedRouteProps {
  /** The admin needs this permission (or any of these). Omit to require only being signed in. */
  permission?: string | readonly string[]
}

/**
 * Wraps routes that need a signed-in admin, and optionally a permission. This is a convenience for
 * the user: the server enforces every permission again, so hiding a page here is never the security.
 */
export function ProtectedRoute({ permission }: ProtectedRouteProps) {
  const { state, hasAnyPermission } = useAuth()
  const location = useLocation()

  if (state.status === 'loading') {
    return <FullPageSpinner />
  }
  if (state.status === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: location }} />
  }
  if (permission !== undefined) {
    const wanted = typeof permission === 'string' ? [permission] : permission
    if (!hasAnyPermission(wanted)) {
      return <ForbiddenPage />
    }
  }
  return <Outlet />
}
