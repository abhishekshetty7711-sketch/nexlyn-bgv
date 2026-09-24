import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from 'react-router-dom'
import { ApiError } from '@/api/httpClient'
import { AuthProvider } from '@/features/auth/AuthProvider'
import { router } from '@/routes'
import './index.css'

// Never retry a request that failed with a 4xx: it will fail the same way, and retries would
// only repeat side effects and delay the message.
const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: (count, error) => count < 2 && !(error instanceof ApiError && error.status < 500) },
  },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </QueryClientProvider>
  </StrictMode>,
)
