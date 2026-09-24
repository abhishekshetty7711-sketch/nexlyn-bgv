import { NavLink, Outlet } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { useAuth } from '@/features/auth/AuthContext'
import { cn } from '@/lib/utils'

interface NavItem {
  to: string
  label: string
  /** Shown only if the admin holds at least one of these. Omit for everyone. UX only: the server decides. */
  anyOf?: readonly string[]
}

const NAV_ITEMS: readonly NavItem[] = [
  { to: '/', label: 'Dashboard' },
  { to: '/cases', label: 'Cases', anyOf: ['CASE_READ_ALL', 'CASE_READ_ASSIGNED'] },
  { to: '/clients', label: 'Clients', anyOf: ['CASE_READ_ALL', 'CASE_READ_ASSIGNED', 'CLIENT_MANAGE'] },
  { to: '/admin-users', label: 'Admins', anyOf: ['USER_MANAGE'] },
  { to: '/roles', label: 'Roles', anyOf: ['ROLE_MANAGE'] },
  { to: '/audit', label: 'Audit Log', anyOf: ['AUDIT_READ'] },
]

export function AppLayout() {
  const { state, hasAnyPermission, signOut, idleSecondsLeft, staySignedIn } = useAuth()
  const me = state.status === 'authenticated' ? state.me : null

  return (
    <div className="flex min-h-screen">
      <aside className="flex w-56 shrink-0 flex-col border-r border-slate-200 bg-white">
        <div className="px-4 py-5 text-lg font-semibold text-slate-900">Nexlyn BGV</div>
        <nav className="flex flex-col gap-1 px-2" aria-label="Main">
          {NAV_ITEMS.filter((item) => !item.anyOf || hasAnyPermission(item.anyOf)).map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) =>
                cn(
                  'rounded-md px-3 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100',
                  isActive && 'bg-slate-900 text-white hover:bg-slate-900',
                )
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        {me && (
          <div className="mt-auto flex flex-col gap-1 border-t border-slate-200 p-3 text-sm">
            <div className="truncate font-medium text-slate-900" title={me.email}>
              {me.fullName}
            </div>
            <div className="truncate text-xs text-slate-500">{me.roles.join(', ')}</div>
            <NavLink to="/account/password" className="mt-1 text-xs text-slate-600 underline">
              Change password
            </NavLink>
            <Button size="sm" variant="outline" className="mt-1" onClick={() => void signOut()}>
              Sign out
            </Button>
          </div>
        )}
      </aside>
      <main className="flex-1 p-6">
        <Outlet />
      </main>
      {idleSecondsLeft !== null && (
        <Dialog title="Still there?" onClose={staySignedIn}>
          <p className="mb-4 text-sm text-slate-600" role="alert">
            You will be signed out in {idleSecondsLeft} {idleSecondsLeft === 1 ? 'second' : 'seconds'} because of
            inactivity.
          </p>
          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={() => void signOut()}>
              Sign out now
            </Button>
            <Button onClick={staySignedIn}>Stay signed in</Button>
          </div>
        </Dialog>
      )}
    </div>
  )
}
