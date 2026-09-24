import { useQuery } from '@tanstack/react-query'

interface HealthResponse {
  status: 'UP' | 'DOWN'
}

async function fetchHealth(): Promise<HealthResponse> {
  // Actuator is mounted outside /api, so this bypasses apiFetch's base path.
  const response = await fetch('/actuator/health')
  if (!response.ok) {
    return { status: 'DOWN' }
  }
  return (await response.json()) as HealthResponse
}

export function useHealthQuery() {
  return useQuery({
    queryKey: ['health'],
    queryFn: fetchHealth,
    refetchInterval: 30_000,
    retry: false,
  })
}
