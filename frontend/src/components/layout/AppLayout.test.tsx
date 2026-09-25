import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { fakeAuth, mockFetch, renderRoutes } from '@/test/testUtils'
import { DashboardPage } from '@/features/dashboard/DashboardPage'
import { AppLayout } from './AppLayout'

const routes = [{ path: '/', element: <AppLayout />, children: [{ index: true, element: <DashboardPage /> }] }]

describe('AppLayout', () => {
  it('renders the nav, the routed page and who is signed in', () => {
    mockFetch({ 'GET /actuator/health': () => ({ body: { status: 'UP' } }) })
    renderRoutes(routes, { auth: fakeAuth() })

    // the name appears in the sidebar and in the slim bar of narrow screens
    expect(screen.getAllByText('Nexlyn BGV').length).toBeGreaterThanOrEqual(1)
    expect(screen.getByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
    expect(screen.getByText('Olivia Owner')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Change password' })).toBeInTheDocument()
  })

  it('has one main landmark and one named navigation, and no unnamed "complementary" sidebar', () => {
    mockFetch({})
    renderRoutes(routes, { auth: fakeAuth() })

    expect(screen.getAllByRole('main')).toHaveLength(1)
    expect(screen.getAllByRole('navigation')).toHaveLength(1)
    expect(screen.getByRole('navigation', { name: 'Main' })).toBeInTheDocument()
    expect(screen.queryByRole('complementary')).not.toBeInTheDocument()
  })

  it('shows only the menu entries the admin can use', () => {
    mockFetch({})
    renderRoutes(routes, { auth: fakeAuth({ permissions: ['CASE_READ_ASSIGNED'] }) })
    const nav = screen.getByRole('navigation', { name: 'Main' })

    expect(nav).toHaveTextContent('Dashboard')
    expect(nav).toHaveTextContent('Cases')
    expect(nav).not.toHaveTextContent('Admins')
    expect(nav).not.toHaveTextContent('Roles')
    expect(nav).not.toHaveTextContent('Audit Log')
  })

  it('shows the management entries to a super admin', () => {
    mockFetch({})
    renderRoutes(routes, { auth: fakeAuth({ permissions: ['USER_MANAGE', 'ROLE_MANAGE', 'AUDIT_READ', 'CASE_READ_ALL'] }) })
    const nav = screen.getByRole('navigation', { name: 'Main' })
    for (const label of ['Cases', 'Clients', 'Admins', 'Roles', 'Audit Log']) {
      expect(nav).toHaveTextContent(label)
    }
  })

  it('signs out when asked', async () => {
    mockFetch({})
    const signOut = vi.fn().mockResolvedValue(undefined)
    renderRoutes(routes, { auth: fakeAuth({ signOut }) })

    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }))
    expect(signOut).toHaveBeenCalledTimes(1)
  })

  it('warns before an idle sign-out and lets the admin stay signed in', async () => {
    mockFetch({})
    const staySignedIn = vi.fn()
    renderRoutes(routes, { auth: fakeAuth({ idleSecondsLeft: 42, staySignedIn }) })

    const dialog = screen.getByRole('dialog', { name: 'Still there?' })
    expect(dialog).toHaveTextContent('signed out in 42 seconds')

    await userEvent.click(screen.getByRole('button', { name: 'Stay signed in' }))
    expect(staySignedIn).toHaveBeenCalled()
  })

  it('shows no warning while the admin is active', () => {
    mockFetch({})
    renderRoutes(routes, { auth: fakeAuth({ idleSecondsLeft: null }) })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('has a skip link to the main content', () => {
    mockFetch({})
    renderRoutes(routes, { auth: fakeAuth() })
    expect(screen.getByRole('link', { name: 'Skip to content' })).toHaveAttribute('href', '#main')
    expect(screen.getByRole('main')).toHaveAttribute('id', 'main')
  })

  it('opens and closes the menu on a narrow screen, with Escape too', async () => {
    mockFetch({})
    renderRoutes(routes, { auth: fakeAuth() })
    const button = screen.getByRole('button', { name: 'Open menu' })
    expect(button).toHaveAttribute('aria-expanded', 'false')
    expect(button).toHaveAttribute('aria-controls', 'app-menu')

    await userEvent.click(button)
    expect(screen.getByRole('button', { name: 'Close menu' })).toHaveAttribute('aria-expanded', 'true')

    await userEvent.keyboard('{Escape}')
    expect(screen.getByRole('button', { name: 'Open menu' })).toHaveAttribute('aria-expanded', 'false')
  })

  it('moves the focus to the new page content after navigating, not on the first load', async () => {
    mockFetch({})
    const pages = [
      {
        path: '/',
        element: <AppLayout />,
        children: [
          { index: true, element: <h1>Home page</h1> },
          { path: 'cases', element: <h1>Cases page</h1> },
        ],
      },
    ]
    renderRoutes(pages, { auth: fakeAuth({ permissions: ['CASE_READ_ALL'] }) })
    expect(screen.getByRole('main')).not.toHaveFocus()

    await userEvent.click(screen.getByRole('link', { name: 'Cases' }))
    expect(await screen.findByRole('heading', { name: 'Cases page' })).toBeInTheDocument()
    expect(screen.getByRole('main')).toHaveFocus()
  })
})
