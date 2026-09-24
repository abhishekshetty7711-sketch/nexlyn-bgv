import { createBrowserRouter } from 'react-router-dom'
import { AppLayout } from '@/components/layout/AppLayout'
import { NotFoundPage } from '@/components/NotFoundPage'
import { Spinner } from '@/components/ui/spinner'
import { AcceptInvitationPage } from '@/features/auth/AcceptInvitationPage'
import { LoginPage } from '@/features/auth/LoginPage'
import { ProtectedRoute } from '@/features/auth/ProtectedRoute'

// Every route below the guard needs a signed-in admin; some also need a permission. That is a
// convenience for the user: the server enforces every permission again on every request.
//
// Pages are loaded when first opened (`lazy`), so signing in does not download the whole application:
// the case workspace, the report screens, the admin screens and the QR-code library each arrive as a
// separate file the first time they are needed.
export const routes = [
  { path: '/login', element: <LoginPage /> },
  { path: '/accept-invite', element: <AcceptInvitationPage /> },
  {
    element: <ProtectedRoute />,
    // shown while the first page of a fresh visit is still being fetched
    HydrateFallback: Spinner,
    children: [
      {
        element: <AppLayout />,
        children: [
          { index: true, lazy: async () => ({ Component: (await import('@/features/dashboard/DashboardPage')).DashboardPage }) },
          { path: 'account/password', lazy: async () => ({ Component: (await import('@/features/auth/ChangePasswordPage')).ChangePasswordPage }) },
          {
            element: <ProtectedRoute permission={['CASE_READ_ALL', 'CASE_READ_ASSIGNED']} />,
            children: [
              { path: 'cases', lazy: async () => ({ Component: (await import('@/features/cases/CasesPage')).CasesPage }) },
              { path: 'cases/:id', lazy: async () => ({ Component: (await import('@/features/cases/workspace/CaseWorkspacePage')).CaseWorkspacePage }) },
            ],
          },
          {
            element: <ProtectedRoute permission={['CASE_READ_ALL', 'CASE_READ_ASSIGNED', 'CLIENT_MANAGE']} />,
            children: [{ path: 'clients', lazy: async () => ({ Component: (await import('@/features/cases/ClientsPage')).ClientsPage }) }],
          },
          {
            element: <ProtectedRoute permission="USER_MANAGE" />,
            children: [{ path: 'admin-users', lazy: async () => ({ Component: (await import('@/features/admins/AdminsPage')).AdminsPage }) }],
          },
          {
            element: <ProtectedRoute permission={['ROLE_MANAGE']} />,
            children: [{ path: 'roles', lazy: async () => ({ Component: (await import('@/features/roles/RolesPage')).RolesPage }) }],
          },
          {
            element: <ProtectedRoute permission="AUDIT_READ" />,
            children: [{ path: 'audit', lazy: async () => ({ Component: (await import('@/features/audit/AuditPage')).AuditPage }) }],
          },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
  { path: '*', element: <NotFoundPage /> },
]

export const router = createBrowserRouter(routes)
