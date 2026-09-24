import type { ReactNode } from 'react'
import { useAuth } from './AuthContext'

interface CanProps {
  /** One permission, or a list of which any one is enough. */
  permission: string | readonly string[]
  children: ReactNode
  fallback?: ReactNode
}

/**
 * Shows its children only if the signed-in admin holds the permission. UX only (it hides buttons
 * the admin could not use anyway); the backend refuses forbidden calls regardless.
 */
export function Can({ permission, children, fallback = null }: CanProps) {
  const { hasAnyPermission } = useAuth()
  const wanted = typeof permission === 'string' ? [permission] : permission
  return <>{hasAnyPermission(wanted) ? children : fallback}</>
}
