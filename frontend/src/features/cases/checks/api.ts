import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '@/api/httpClient'
import type { CheckStatus, CheckTypeDef, CheckView } from './types'

export function useCheckTypes() {
  return useQuery({
    queryKey: ['check-types'],
    queryFn: () => apiFetch<CheckTypeDef[]>('/check-types'),
    staleTime: Infinity,
  })
}

const checksKey = (caseId: string) => ['checks', caseId] as const

export function useChecks(caseId: string) {
  return useQuery({ queryKey: checksKey(caseId), queryFn: () => apiFetch<CheckView[]>(`/cases/${caseId}/checks`) })
}

/** After any change to checks: refresh the list (saving the first check moves the others) and what depends on it. */
function useRefreshAfterChange(caseId: string) {
  const queryClient = useQueryClient()
  return () => {
    void queryClient.invalidateQueries({ queryKey: checksKey(caseId) })
    void queryClient.invalidateQueries({ queryKey: ['case', caseId] }) // overview numbers come from the checks
    void queryClient.invalidateQueries({ queryKey: ['case-progress', caseId] })
    void queryClient.invalidateQueries({ queryKey: ['case-validation', caseId] })
    void queryClient.invalidateQueries({ queryKey: ['cases'] })
  }
}

export function useAddCheck(caseId: string) {
  const refresh = useRefreshAfterChange(caseId)
  return useMutation({
    mutationFn: (type: string) => apiFetch<CheckView>(`/cases/${caseId}/checks`, { json: { type } }),
    onSuccess: refresh,
  })
}

export interface FieldInput {
  key: string
  value?: string
  verifiedTick?: boolean
  manual?: boolean
  clear?: boolean
}

export interface SaveCheckInput {
  version: number
  title: string
  summaryDescription: string | null
  thisCardVerifies: string | null
  status: CheckStatus
  verificationType: string
  requestedDate: string | null
  completedDate: string | null
  remarks: string | null
  hasAttestation: boolean
  barCouncilNo: string | null
  disclaimer: string | null
  fields: FieldInput[]
  details: { label: string; value: string | null }[]
}

export function useSaveCheck(caseId: string, checkId: string) {
  const refresh = useRefreshAfterChange(caseId)
  return useMutation({
    mutationFn: (input: SaveCheckInput) =>
      apiFetch<CheckView>(`/cases/${caseId}/checks/${checkId}`, { method: 'PUT', json: input }),
    onSuccess: refresh,
  })
}

export function useDeleteCheck(caseId: string) {
  const refresh = useRefreshAfterChange(caseId)
  return useMutation({
    mutationFn: (checkId: string) => apiFetch<void>(`/cases/${caseId}/checks/${checkId}`, { method: 'DELETE' }),
    onSuccess: refresh,
  })
}

export function useReorderChecks(caseId: string) {
  const refresh = useRefreshAfterChange(caseId)
  return useMutation({
    mutationFn: (ids: string[]) => apiFetch<CheckView[]>(`/cases/${caseId}/checks/order`, { method: 'PATCH', json: { ids } }),
    onSuccess: refresh,
  })
}

export function useAddFreeSection(caseId: string, checkId: string) {
  const refresh = useRefreshAfterChange(caseId)
  return useMutation({
    mutationFn: (text: string) =>
      apiFetch<CheckView>(`/cases/${caseId}/checks/${checkId}/free-sections`, { json: { kind: 'TEXT', text } }),
    onSuccess: refresh,
  })
}

/** An image block: the picture is uploaded first (as a picture for this check), then attached as a block. */
export function useAddFreeImage(caseId: string, checkId: string) {
  const refresh = useRefreshAfterChange(caseId)
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (file: File) => {
      const form = new FormData()
      form.append('file', file)
      const picture = await apiFetch<{ id: string }>(`/checks/${checkId}/documents?kind=FREE_IMAGE`, { form })
      return apiFetch<CheckView>(`/cases/${caseId}/checks/${checkId}/free-sections`, { json: { kind: 'IMAGE', documentId: picture.id } })
    },
    onSuccess: () => {
      refresh()
      void queryClient.invalidateQueries({ queryKey: ['documents', checkId] })
    },
  })
}

export function useUpdateFreeSection(caseId: string, checkId: string) {
  const refresh = useRefreshAfterChange(caseId)
  return useMutation({
    mutationFn: ({ id, text }: { id: string; text: string }) =>
      apiFetch<CheckView>(`/cases/${caseId}/checks/${checkId}/free-sections/${id}`, { method: 'PUT', json: { text } }),
    onSuccess: refresh,
  })
}

export function useDeleteFreeSection(caseId: string, checkId: string) {
  const refresh = useRefreshAfterChange(caseId)
  return useMutation({
    mutationFn: (id: string) =>
      apiFetch<CheckView>(`/cases/${caseId}/checks/${checkId}/free-sections/${id}`, { method: 'DELETE' }),
    onSuccess: refresh,
  })
}

/**
 * Fetches one real Aadhaar / PAN / UAN. Deliberately NOT a query: the value must never sit in the
 * query cache. The caller keeps it in component state only, and hides it again after a short time.
 */
export function revealField(caseId: string, checkId: string, fieldKey: string): Promise<{ fieldKey: string; value: string }> {
  return apiFetch(`/cases/${caseId}/checks/${checkId}/fields/${fieldKey}/reveal`)
}
