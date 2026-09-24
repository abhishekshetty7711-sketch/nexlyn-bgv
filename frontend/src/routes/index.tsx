import { createBrowserRouter } from 'react-router-dom'
import { CasesPage } from '@/features/cases/CasesPage'
import { ClientsPage } from '@/features/cases/ClientsPage'
import { CaseWorkspacePage } from '@/features/cases/workspace/CaseWorkspacePage'
import { AdminsPage } from '@/features/admins/AdminsPage'
import { AuditPage } from '@/features/audit/AuditPage'
import { AcceptInvitationPage } from '@/features/auth/AcceptInvitationPage'
import { ChangePasswordPage } from '@/features/auth/ChangePasswordPage'
import { LoginPage } from '@/features/auth/LoginPage'
import { ProtectedRoute } from '@/features/auth/ProtectedRoute'
import { RolesPage } from '@/features/roles/RolesPage'
import { AppLayout } from '@/components/layout/AppLayout'
import { NotFoundPage } from '@/components/NotFoundPage'
import { DashboardPage } from '@/features/dashboard/DashboardPage'

// Every route below the guard needs a signed-in admin; some also need a permission. That is a
// convenience for the user: the server enforces every permission again on every request.
export const routes = [
  { path: '/login', element: <LoginPage /> },
  { path: '/accept-invite', element: <AcceptInvitationPage /> },
  {
    element: <ProtectedRoute />,
    children: [
      {
        element: <AppLayout />,
        children: [
          { index: true, element: <DashboardPage /> },
          { path: 'account/password', element: <ChangePasswordPage /> },
          {
            element: <ProtectedRoute permission={['CASE_READ_ALL', 'CASE_READ_ASSIGNED']} />,
            children: [
              { path: 'cases', element: <CasesPage /> },
              { path: 'cases/:id', element: <CaseWorkspacePage /> },
            ],
          },
          {
            element: <ProtectedRoute permission={['CASE_READ_ALL', 'CASE_READ_ASSIGNED', 'CLIENT_MANAGE']} />,
            children: [{ path: 'clients', element: <ClientsPage /> }],
          },
          {
            element: <ProtectedRoute permission="USER_MANAGE" />,
            children: [{ path: 'admin-users', element: <AdminsPage /> }],
          },
          {
            element: <ProtectedRoute permission={['ROLE_MANAGE']} />,
            children: [{ path: 'roles', element: <RolesPage /> }],
          },
          {
            element: <ProtectedRoute permission="AUDIT_READ" />,
            children: [{ path: 'audit', element: <AuditPage /> }],
          },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
  { path: '*', element: <NotFoundPage /> },
]

export const router = createBrowserRouter(routes)
