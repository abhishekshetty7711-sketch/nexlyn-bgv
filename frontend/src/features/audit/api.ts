import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { apiFetch } from '@/api/httpClient'
import type { Page } from '@/api/types'

export interface AuditEntry {
  id: string
  at: string
  actorId: string | null
  actorEmail: string | null
  action: string
  entityType: string | null
  entityId: string | null
  caseId: string | null
  ip: string | null
  before: Record<string, unknown> | null
  after: Record<string, unknown> | null
  correlationId: string | null
}

export interface AuditFilters {
  actor: string
  action: string
  entity: string
  /** `datetime-local` value (no zone): converted to an instant when sent. */
  from: string
  to: string
}

export const EMPTY_FILTERS: AuditFilters = { actor: '', action: '', entity: '', from: '', to: '' }
export const AUDIT_PAGE_SIZE = 50

/** A `datetime-local` value is in the browser's time zone; the API wants an ISO instant. */
export function toInstant(localValue: string): string | null {
  if (!localValue) {
    return null
  }
  const date = new Date(localValue)
  return Number.isNaN(date.getTime()) ? null : date.toISOString()
}

export function buildAuditQuery(filters: AuditFilters, page: number): string {
  const params = new URLSearchParams({ page: String(page), size: String(AUDIT_PAGE_SIZE) })
  const text: [string, string][] = [
    ['actor', filters.actor.trim()],
    ['action', filters.action.trim()],
    ['entity', filters.entity.trim()],
  ]
  for (const [name, value] of text) {
    if (value) {
      params.set(name, value)
    }
  }
  const from = toInstant(filters.from)
  const to = toInstant(filters.to)
  if (from) {
    params.set('from', from)
  }
  if (to) {
    params.set('to', to)
  }
  return params.toString()
}

export function useAuditLog(filters: AuditFilters, page: number) {
  return useQuery({
    queryKey: ['audit-log', filters, page],
    queryFn: () => apiFetch<Page<AuditEntry>>(`/audit-log?${buildAuditQuery(filters, page)}`),
    placeholderData: keepPreviousData,
  })
}
