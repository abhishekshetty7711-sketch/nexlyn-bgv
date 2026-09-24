import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { AppLayout } from './AppLayout'
import { DashboardPage } from '@/features/dashboard/DashboardPage'

describe('AppLayout', () => {
  it('renders the nav and the routed page', () => {
    const router = createMemoryRouter([
      { path: '/', element: <AppLayout />, children: [{ index: true, element: <DashboardPage /> }] },
    ])
    const queryClient = new QueryClient()

    render(
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>,
    )

    expect(screen.getByText('Nexlyn BGV')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
  })
})
