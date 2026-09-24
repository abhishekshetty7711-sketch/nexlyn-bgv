import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '@/api/httpClient'
import type { Crop, DocumentKind, DocumentView } from './types'

const documentsKey = (checkId: string) => ['documents', checkId] as const

export function useDocuments(checkId: string) {
  return useQuery({ queryKey: documentsKey(checkId), queryFn: () => apiFetch<DocumentView[]>(`/checks/${checkId}/documents`) })
}

/** After a file change: the list, and what the case derives from it (photo, the "no document" warnings). */
function useRefreshAfterChange(caseId: string, checkId: string | null) {
  const queryClient = useQueryClient()
  return () => {
    if (checkId) {
      void queryClient.invalidateQueries({ queryKey: documentsKey(checkId) })
      void queryClient.invalidateQueries({ queryKey: ['checks', caseId] })
    }
    void queryClient.invalidateQueries({ queryKey: ['case', caseId] })
    void queryClient.invalidateQueries({ queryKey: ['case-progress', caseId] })
    void queryClient.invalidateQueries({ queryKey: ['case-validation', caseId] })
  }
}

function formOf(file: File): FormData {
  const form = new FormData()
  form.append('file', file)
  return form
}

/** Uploads one file to a check: a supporting document, or a picture for an image block. */
export function useUploadToCheck(caseId: string, checkId: string) {
  const refresh = useRefreshAfterChange(caseId, checkId)
  return useMutation({
    mutationFn: ({ file, kind }: { file: File; kind: DocumentKind }) =>
      apiFetch<DocumentView>(`/checks/${checkId}/documents?kind=${kind}`, { form: formOf(file) }),
    onSuccess: refresh,
  })
}

export interface DocumentChanges {
  label: string | null
  moveToNextPage: boolean
  useLargerBox: boolean
  crop: Crop | null
  version: number
}

export function useUpdateDocument(caseId: string, checkId: string) {
  const refresh = useRefreshAfterChange(caseId, checkId)
  return useMutation({
    mutationFn: ({ id, changes }: { id: string; changes: DocumentChanges }) =>
      apiFetch<DocumentView>(`/documents/${id}`, { method: 'PUT', json: changes }),
    onSuccess: refresh,
  })
}

export function useReorderDocuments(caseId: string, checkId: string) {
  const refresh = useRefreshAfterChange(caseId, checkId)
  return useMutation({
    mutationFn: (ids: string[]) => apiFetch<DocumentView[]>(`/checks/${checkId}/documents/order`, { method: 'PATCH', json: { ids } }),
    onSuccess: refresh,
  })
}

export function useDeleteDocument(caseId: string, checkId: string) {
  const refresh = useRefreshAfterChange(caseId, checkId)
  return useMutation({
    mutationFn: (id: string) => apiFetch<void>(`/documents/${id}`, { method: 'DELETE' }),
    onSuccess: refresh,
  })
}

export function useUploadPhoto(caseId: string) {
  const refresh = useRefreshAfterChange(caseId, null)
  return useMutation({
    mutationFn: (file: File) => apiFetch<DocumentView>(`/cases/${caseId}/candidate/photo`, { form: formOf(file) }),
    onSuccess: refresh,
  })
}

export function useDeletePhoto(caseId: string) {
  const refresh = useRefreshAfterChange(caseId, null)
  return useMutation({
    mutationFn: () => apiFetch<void>(`/cases/${caseId}/candidate/photo`, { method: 'DELETE' }),
    onSuccess: refresh,
  })
}

/** The file itself. Fetched with the bearer token (an <img> tag cannot send one), so it is bytes, not a URL. */
export function fetchDocumentBlob(documentId: string): Promise<Blob> {
  return apiFetch<Blob>(`/documents/${documentId}/content`, { as: 'blob' })
}

function blobToDataUrl(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result))
    reader.onerror = () => reject(reader.error ?? new Error('Could not read the picture'))
    reader.readAsDataURL(blob)
  })
}

/**
 * A stored picture as a data URL, kept briefly in memory (a plain image tag cannot send the sign-in
 * token, and a data URL needs no clean-up). Every fetch is recorded in the audit log by the server, so
 * a few minutes of caching keeps that log about people looking, not about scrolling.
 */
export function useDocumentImage(documentId: string | null) {
  return useQuery({
    queryKey: ['document-image', documentId],
    queryFn: async () => blobToDataUrl(await fetchDocumentBlob(documentId!)),
    enabled: documentId !== null,
    staleTime: 5 * 60_000,
    gcTime: 60_000,
    retry: false,
  })
}
