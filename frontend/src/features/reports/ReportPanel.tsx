import { useState } from 'react'
import { describeError } from '@/api/errors'
import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { Spinner } from '@/components/ui/spinner'
import { useAuth } from '@/features/auth/AuthContext'
import { formatBytes } from '../documents/files'
import { downloadReport, fetchPreviewHtml, type ReportVersion, useGenerateReport, useReportJob, useReportVersions } from './api'

interface ReportPanelProps {
  caseId: string
  reportId: string
  /** The case has errors: the report cannot be generated yet (a preview still works). */
  hasErrors: boolean
  warningCount: number
}

function whenText(iso: string): string {
  return new Date(iso).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' })
}

/** Section 8: look at the report, make a draft PDF, and download the versions made so far. */
export function ReportPanel({ caseId, reportId, hasErrors, warningCount }: ReportPanelProps) {
  const { hasPermission } = useAuth()
  const versions = useReportVersions(caseId)
  const generate = useGenerateReport(caseId)
  const [jobId, setJobId] = useState<string | null>(null)
  const job = useReportJob(caseId, jobId)
  const [previewHtml, setPreviewHtml] = useState<string | null>(null)
  const [previewing, setPreviewing] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [problem, setProblem] = useState<string | null>(null)

  const canGenerate = hasPermission('REPORT_GENERATE')
  const canDownloadFinal = hasPermission('REPORT_DOWNLOAD_FINAL')
  const making = job.data ? job.data.status === 'QUEUED' || job.data.status === 'RUNNING' : generate.isPending
  const finished = job.data && (job.data.status === 'DONE' || job.data.status === 'FAILED') ? job.data : null

  async function openPreview() {
    setProblem(null)
    setPreviewing(true)
    try {
      setPreviewHtml(await fetchPreviewHtml(caseId))
    } catch (error) {
      setProblem(describeError(error))
    } finally {
      setPreviewing(false)
    }
  }

  async function start(acknowledge: boolean) {
    setConfirming(false)
    setProblem(null)
    try {
      const started = await generate.mutateAsync(acknowledge)
      setJobId(started.id)
    } catch (error) {
      setProblem(describeError(error))
    }
  }

  async function save(version: ReportVersion) {
    setProblem(null)
    try {
      await downloadReport(caseId, version.version, reportId)
    } catch (error) {
      setProblem(describeError(error))
    }
  }

  return (
    <section className="flex flex-col gap-3 border-t border-slate-200 pt-4" aria-label="Report">
      <div className="flex flex-wrap items-center gap-2">
        <Button type="button" variant="outline" disabled={previewing} onClick={() => void openPreview()}>
          {previewing ? 'Loading preview...' : 'Preview'}
        </Button>
        {canGenerate && (
          <Button type="button" variant="outline" disabled={hasErrors || making} onClick={() => (warningCount > 0 ? setConfirming(true) : void start(false))}>
            {making ? 'Making the report...' : 'Generate draft PDF'}
          </Button>
        )}
      </div>
      {hasErrors && canGenerate && <p className="text-xs text-slate-500">Fix the errors above to generate the PDF. The preview works meanwhile.</p>}

      {problem && <Alert variant="error">{problem}</Alert>}
      {making && (
        <div role="status" className="flex items-center gap-2 text-sm text-slate-600">
          <Spinner /> Making the report. This takes a few seconds; you can keep working.
        </div>
      )}
      {finished?.status === 'FAILED' && <Alert variant="error">{finished.error ?? 'The report could not be generated.'}</Alert>}
      {finished?.status === 'DONE' && (
        <Alert variant="success">
          Report version {finished.version} is ready below.
        </Alert>
      )}
      {finished?.status === 'DONE' && finished.warnings.length > 0 && (
        <Alert variant="warning">
          <p className="mb-1 font-medium">Some pages need attention:</p>
          <ul className="list-disc pl-4">
            {finished.warnings.map((warning) => (
              <li key={warning}>{warning}</li>
            ))}
          </ul>
        </Alert>
      )}

      <h3 className="text-sm font-semibold text-slate-800">Versions</h3>
      {versions.isLoading && <Spinner />}
      {versions.isError && <Alert variant="error">{describeError(versions.error)}</Alert>}
      {versions.isSuccess && versions.data.length === 0 && <p className="text-sm text-slate-500">No report has been generated yet.</p>}
      {versions.isSuccess && versions.data.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm" aria-label="Report versions">
            <thead>
              <tr className="border-b border-slate-200 text-xs uppercase text-slate-500">
                <th className="py-2 pr-3">Version</th>
                <th className="py-2 pr-3">Type</th>
                <th className="py-2 pr-3">Pages</th>
                <th className="py-2 pr-3">Size</th>
                <th className="py-2 pr-3">Made by</th>
                <th className="py-2 pr-3">When</th>
                <th className="py-2" />
              </tr>
            </thead>
            <tbody>
              {versions.data.map((version) => (
                <tr key={version.version} className="border-b border-slate-100">
                  <td className="py-2 pr-3 font-medium">v{version.version}</td>
                  <td className="py-2 pr-3">
                    <Badge tone={version.kind === 'FINAL' ? 'green' : 'neutral'}>{version.kind === 'FINAL' ? 'Final' : 'Draft'}</Badge>
                    {version.encrypted && <span className="ml-1 text-xs text-slate-500">password protected</span>}
                  </td>
                  <td className="py-2 pr-3">{version.pageCount}</td>
                  <td className="py-2 pr-3">{formatBytes(version.sizeBytes)}</td>
                  <td className="py-2 pr-3">{version.generatedByName}</td>
                  <td className="py-2 pr-3">{whenText(version.generatedAt)}</td>
                  <td className="py-2 text-right">
                    {(version.kind === 'DRAFT' || canDownloadFinal) && (
                      <Button type="button" size="sm" variant="outline" onClick={() => void save(version)} aria-label={`Download version ${version.version}`}>
                        Download
                      </Button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {previewHtml !== null && (
        <Dialog title="Report preview" size="wide" onClose={() => setPreviewHtml(null)}>
          <iframe title="Report preview" sandbox="" srcDoc={previewHtml} className="h-[70vh] w-full rounded border border-slate-200" />
          <div className="mt-3 flex justify-end">
            <Button type="button" variant="outline" onClick={() => setPreviewHtml(null)}>
              Close
            </Button>
          </div>
        </Dialog>
      )}
      {confirming && (
        <Dialog title="Generate with warnings?" onClose={() => setConfirming(false)}>
          <p className="mb-4 text-sm text-slate-600">
            This case has {warningCount} warning{warningCount === 1 ? '' : 's'} (see the list above). The report can still be generated; the things
            marked will simply be missing or unfinished in it.
          </p>
          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={() => setConfirming(false)}>
              Go back
            </Button>
            <Button onClick={() => void start(true)}>Generate anyway</Button>
          </div>
        </Dialog>
      )}
    </section>
  )
}
