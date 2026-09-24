import { NavLink, Outlet } from 'react-router-dom'
import { cn } from '@/lib/utils'

const NAV_ITEMS = [
  { to: '/', label: 'Dashboard' },
  { to: '/cases', label: 'Cases' },
  { to: '/clients', label: 'Clients' },
  { to: '/admin-users', label: 'Admins' },
  { to: '/audit', label: 'Audit Log' },
]

export function AppLayout() {
  return (
    <div className="flex min-h-screen">
      <aside className="w-56 shrink-0 border-r border-slate-200 bg-white">
        <div className="px-4 py-5 text-lg font-semibold text-slate-900">Nexlyn BGV</div>
        <nav className="flex flex-col gap-1 px-2">
          {NAV_ITEMS.map((item) => (
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
      </aside>
      <main className="flex-1 p-6">
        <Outlet />
      </main>
    </div>
  )
}
