import { createBrowserRouter } from 'react-router-dom'
import { AppLayout } from '@/components/layout/AppLayout'
import { DashboardPage } from '@/features/dashboard/DashboardPage'

// Each route lists the permission it will require once Phase 2 wires up
// ProtectedRoute + auth. Left unenforced here since there is no auth yet.
export const router = createBrowserRouter([
  {
    path: '/',
    element: <AppLayout />,
    children: [{ index: true, element: <DashboardPage /> }],
  },
])
