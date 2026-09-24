import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '@/api/httpClient'
import type { Page } from '@/api/types'
import type {
  AdminSummary,
  CaseRole,
  CaseRow,
  CaseView,
  ClientView,
  Lifecycle,
  Progress,
  ValidationResult,
} from './types'

export const CASES_PAGE_SIZE = 25

export interface CaseFilters {
  status: Lifecycle | ''
  client: string
  q: string
}

export const EMPTY_CASE_FILTERS: CaseFilters = { status: '', client: '', q: '' }

export function buildCaseQuery(filters: CaseFilters, page: number): string {
  const params = new URLSearchParams({ page: String(page), size: String(CASES_PAGE_SIZE) })
  if (filters.status) {
    params.set('status', filters.status)
  }
  if (filters.client) {
    params.set('client', filters.client)
  }
  if (filters.q.trim()) {
    params.set('q', filters.q.trim())
  }
  return params.toString()
}

const caseKey = (id: string) => ['case', id] as const

export function useCases(filters: CaseFilters, page: number) {
  return useQuery({
    queryKey: ['cases', filters, page],
    queryFn: () => apiFetch<Page<CaseRow>>(`/cases?${buildCaseQuery(filters, page)}`),
    placeholderData: keepPreviousData,
  })
}

export function useCase(id: string) {
  return useQuery({ queryKey: caseKey(id), queryFn: () => apiFetch<CaseView>(`/cases/${id}`) })
}

export function useProgress(id: string) {
  return useQuery({ queryKey: ['case-progress', id], queryFn: () => apiFetch<Progress>(`/cases/${id}/progress`) })
}

export function useValidation(id: string) {
  return useQuery({
    queryKey: ['case-validation', id],
    queryFn: () => apiFetch<ValidationResult>(`/cases/${id}/validation`),
  })
}

export function useClients(active?: boolean) {
  const suffix = active === undefined ? '' : `?active=${active}`
  return useQuery({ queryKey: ['clients', active ?? 'all'], queryFn: () => apiFetch<ClientView[]>(`/clients${suffix}`) })
}

export function useAssignableAdmins(enabled: boolean) {
  return useQuery({
    queryKey: ['assignable-admins'],
    queryFn: () => apiFetch<AdminSummary[]>('/assignable-admins'),
    enabled,
  })
}

export interface NewCaseInput {
  clientId: string
  issueDate?: string
  dueDate?: string
}

export function useCreateCase() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: NewCaseInput) => apiFetch<CaseView>('/cases', { json: input }),
    onSuccess: (created) => {
      queryClient.setQueryData(caseKey(created.id), created)
      void queryClient.invalidateQueries({ queryKey: ['cases'] })
    },
  })
}

/** Section names as the API spells them in the URL. */
export type SaveSection = 'report-info' | 'candidate' | 'verification-period' | 'overview' | 'remarks' | 'settings'

/**
 * Saves one section. The current `version` is added for you: the server refuses the save (409) if
 * someone else saved in the meantime. The answer is the whole updated case, which replaces the cache,
 * so every screen shows the same, latest data.
 */
export function useSaveSection(caseId: string, section: SaveSection) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ version, values }: { version: number; values: Record<string, unknown> }) =>
      apiFetch<CaseView>(`/cases/${caseId}/${section}`, { method: 'PUT', json: { version, ...values } }),
    onSuccess: (updated) => {
      queryClient.setQueryData(caseKey(caseId), updated)
      void queryClient.invalidateQueries({ queryKey: ['case-progress', caseId] })
      void queryClient.invalidateQueries({ queryKey: ['case-validation', caseId] })
      void queryClient.invalidateQueries({ queryKey: ['cases'] })
    },
  })
}

export function useAssign(caseId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { adminId: string; role: CaseRole }) =>
      apiFetch<CaseView>(`/cases/${caseId}/assignments`, { json: input }),
    onSuccess: (updated) => {
      queryClient.setQueryData(caseKey(caseId), updated)
      void queryClient.invalidateQueries({ queryKey: ['cases'] })
    },
  })
}

export function useUnassign(caseId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { adminId: string; role: CaseRole }) =>
      apiFetch<CaseView>(`/cases/${caseId}/assignments/${input.adminId}?role=${input.role}`, { method: 'DELETE' }),
    onSuccess: (updated) => {
      queryClient.setQueryData(caseKey(caseId), updated)
      void queryClient.invalidateQueries({ queryKey: ['cases'] })
    },
  })
}

export interface ClientInput {
  name: string
  displayName: string
  defaultCheckTypes: string[]
  active: boolean
}

export function useSaveClient() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, version, input }: { id?: string; version?: number; input: ClientInput }) =>
      id
        ? apiFetch<ClientView>(`/clients/${id}`, { method: 'PUT', json: { version, ...input } })
        : apiFetch<ClientView>('/clients', { json: input }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['clients'] }),
  })
}
