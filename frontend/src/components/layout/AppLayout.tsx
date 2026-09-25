import { useEffect, useRef, useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { Briefcase, Building2, LayoutDashboard, LogOut, Menu, ScrollText, ShieldCheck, Users, X, type LucideIcon } from 'lucide-react'
import logoUrl from '@/assets/nexlyn-logo.jpg'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { useAuth } from '@/features/auth/AuthContext'
import { cn } from '@/lib/utils'

interface NavItem {
  to: string
  label: string
  icon: LucideIcon
  /** Shown only if the admin holds at least one of these. Omit for everyone. UX only: the server decides. */
  anyOf?: readonly string[]
}

const NAV_ITEMS: readonly NavItem[] = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/cases', label: 'Cases', icon: Briefcase, anyOf: ['CASE_READ_ALL', 'CASE_READ_ASSIGNED'] },
  { to: '/clients', label: 'Clients', icon: Building2, anyOf: ['CASE_READ_ALL', 'CASE_READ_ASSIGNED', 'CLIENT_MANAGE'] },
  { to: '/admin-users', label: 'Admins', icon: Users, anyOf: ['USER_MANAGE'] },
  { to: '/roles', label: 'Roles', icon: ShieldCheck, anyOf: ['ROLE_MANAGE'] },
  { to: '/audit', label: 'Audit Log', icon: ScrollText, anyOf: ['AUDIT_READ'] },
]

export function AppLayout() {
  const { state, hasAnyPermission, signOut, idleSecondsLeft, staySignedIn } = useAuth()
  const me = state.status === 'authenticated' ? state.me : null
  const location = useLocation()
  // The path the narrow-screen menu was opened on: going to another page closes it (no state is set in an effect).
  const [openedOn, setOpenedOn] = useState<string | null>(null)
  const menuOpen = openedOn === location.pathname
  const setMenuOpen = (open: boolean) => setOpenedOn(open ? location.pathname : null)
  const main = useRef<HTMLElement>(null)
  const firstRender = useRef(true)

  // After moving to another page, the keyboard and screen reader start at the top of the new content (not on the menu).
  useEffect(() => {
    if (firstRender.current) {
      firstRender.current = false
      return
    }
    main.current?.focus()
  }, [location.pathname])

  useEffect(() => {
    if (!menuOpen) return
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpenedOn(null)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [menuOpen])

  return (
    <div className="flex min-h-screen flex-col lg:flex-row">
      <a
        href="#main"
        className="sr-only z-50 rounded-md bg-white px-3 py-2 text-sm font-medium text-brand-800 shadow focus:not-sr-only focus:fixed focus:left-3 focus:top-3"
      >
        Skip to content
      </a>

      {/* Narrow screens: a slim bar with the menu button; the menu slides in over the page. */}
      <header className="sticky top-0 z-20 flex items-center gap-3 bg-brand-800 px-4 py-2.5 text-white lg:hidden">
        <button
          type="button"
          onClick={() => setMenuOpen(!menuOpen)}
          aria-expanded={menuOpen}
          aria-controls="app-menu"
          aria-label={menuOpen ? 'Close menu' : 'Open menu'}
          className="rounded-md p-1.5 hover:bg-white/10 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white"
        >
          {menuOpen ? <X className="h-5 w-5" aria-hidden /> : <Menu className="h-5 w-5" aria-hidden />}
        </button>
        <span className="font-semibold">Nexlyn BGV</span>
      </header>
      {menuOpen && <div className="fixed inset-0 z-20 bg-slate-900/50 lg:hidden" aria-hidden onClick={() => setMenuOpen(false)} />}

      <div
        id="app-menu"
        className={cn(
          'fixed inset-y-0 left-0 z-30 flex w-64 shrink-0 -translate-x-full flex-col bg-brand-800 text-white transition-transform lg:sticky lg:top-0 lg:h-screen lg:translate-x-0',
          // closed on a narrow screen: also invisible, so the keyboard cannot reach links that are off the screen
          menuOpen ? 'translate-x-0' : 'max-lg:invisible',
        )}
      >
        <div className="flex items-center gap-3 px-4 py-5">
          <img src={logoUrl} alt="" className="h-10 w-10 shrink-0 rounded-lg bg-white p-0.5" />
          <div className="min-w-0 leading-tight">
            <div className="text-base font-semibold">Nexlyn BGV</div>
            <div className="text-xs text-brand-200">Admin console</div>
          </div>
        </div>
        <nav className="flex flex-col gap-1 px-3" aria-label="Main">
          {NAV_ITEMS.filter((item) => !item.anyOf || hasAnyPermission(item.anyOf)).map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium text-brand-100 transition-colors hover:bg-white/10 hover:text-white',
                  isActive && 'bg-white text-brand-800 hover:bg-white hover:text-brand-800',
                )
              }
            >
              <item.icon className="h-4 w-4 shrink-0" aria-hidden />
              {item.label}
            </NavLink>
          ))}
        </nav>
        {me && (
          <div className="mt-auto flex flex-col gap-1 border-t border-white/15 p-4 text-sm">
            <div className="truncate font-medium" title={me.email}>
              {me.fullName}
            </div>
            <div className="truncate text-xs text-brand-200">{me.roles.join(', ')}</div>
            <NavLink to="/account/password" className="mt-1 inline-flex min-h-6 w-fit items-center text-xs text-brand-100 underline underline-offset-2 hover:text-white">
              Change password
            </NavLink>
            <Button size="sm" variant="outline" className="mt-2 border-transparent" onClick={() => void signOut()}>
              <LogOut className="h-3.5 w-3.5" aria-hidden />
              Sign out
            </Button>
          </div>
        )}
      </div>

      <main id="main" ref={main} tabIndex={-1} className="min-w-0 flex-1 p-4 outline-none sm:p-6 lg:p-8">
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
