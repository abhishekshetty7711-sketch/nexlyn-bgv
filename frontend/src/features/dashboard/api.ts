import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '@/api/httpClient'
import type { CaseRow, Lifecycle } from '../cases/types'

export interface Dashboard {
  counts: Record<Lifecycle, number>
  mine: CaseRow[]
  awaitingMyReview: CaseRow[]
  dueSoon: CaseRow[]
  overdue: number
  /** Today in India, as the server counts due dates. */
  today: string
}

export function useDashboard(enabled = true) {
  return useQuery({ queryKey: ['dashboard'], queryFn: () => apiFetch<Dashboard>('/dashboard'), enabled })
}
