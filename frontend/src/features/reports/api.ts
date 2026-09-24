import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '@/api/httpClient'

export type JobStatus = 'QUEUED' | 'RUNNING' | 'DONE' | 'FAILED'

export interface ReportJob {
  id: string
  caseId: string
  status: JobStatus
  version: number | null
  error: string | null
  /** Pages whose content did not fit, in plain words. */
  warnings: string[]
  requestedAt: string
  finishedAt: string | null
}

export interface ReportVersion {
  version: number
  kind: 'DRAFT' | 'FINAL'
  sizeBytes: number
  pageCount: number
  encrypted: boolean
  generatedByName: string
  generatedAt: string
  finalizedAt: string | null
  warnings: string[]
}

const versionsKey = (caseId: string) => ['report-versions', caseId] as const

export function useReportVersions(caseId: string) {
  return useQuery({ queryKey: versionsKey(caseId), queryFn: () => apiFetch<ReportVersion[]>(`/cases/${caseId}/reports`) })
}

/** The report's pages as HTML, for the preview. Not kept: it is fetched afresh every time it is opened. */
export function fetchPreviewHtml(caseId: string): Promise<string> {
  return apiFetch<string>(`/cases/${caseId}/reports/preview`, { as: 'text' })
}

export function useGenerateReport(caseId: string) {
  return useMutation({
    mutationFn: (acknowledgeWarnings: boolean) => apiFetch<ReportJob>(`/cases/${caseId}/reports`, { json: { acknowledgeWarnings } }),
  })
}

const ACTIVE: JobStatus[] = ['QUEUED', 'RUNNING']

/** The job being made, checked every 1.5 seconds until it is done or failed; then the version list refreshes. */
export function useReportJob(caseId: string, jobId: string | null) {
  const queryClient = useQueryClient()
  return useQuery({
    queryKey: ['report-job', caseId, jobId],
    enabled: jobId !== null,
    queryFn: async () => {
      const job = await apiFetch<ReportJob>(`/cases/${caseId}/reports/jobs/${jobId}`)
      if (!ACTIVE.includes(job.status)) {
        void queryClient.invalidateQueries({ queryKey: versionsKey(caseId) })
      }
      return job
    },
    refetchInterval: (query) => (query.state.data && !ACTIVE.includes(query.state.data.status) ? false : 1500),
  })
}

/** Turns a draft made after the approval into the final, protected report and finalizes the case. */
export function useFinalizeReport(caseId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ version, openPassword }: { version: number; openPassword: string | null }) =>
      apiFetch<ReportVersion>(`/cases/${caseId}/reports/${version}/finalize`, { json: { openPassword } }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['case', caseId] })
      void queryClient.invalidateQueries({ queryKey: versionsKey(caseId) })
      void queryClient.invalidateQueries({ queryKey: ['case-history', caseId] })
      void queryClient.invalidateQueries({ queryKey: ['case-progress', caseId] })
      void queryClient.invalidateQueries({ queryKey: ['cases'] })
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    },
  })
}

/** Fetches the PDF with the sign-in token and hands it to the browser as a file save. */
export async function downloadReport(caseId: string, version: number, reportName: string): Promise<void> {
  const blob = await apiFetch<Blob>(`/cases/${caseId}/reports/${version}/download`, { as: 'blob' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${reportName.replace(/[^A-Za-z0-9_-]/g, '_')}_v${version}.pdf`
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 60_000)
}
