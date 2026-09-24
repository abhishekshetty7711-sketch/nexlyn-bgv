import { describeError } from '@/api/errors'
import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Spinner } from '@/components/ui/spinner'
import { ReportPanel } from '../../reports/ReportPanel'
import { WorkflowPanel } from './WorkflowPanel'
import { useValidation } from '../api'
import type { CaseView, SectionKey, ValidationIssue } from '../types'
import { isSectionKey } from '../types'

interface GenerateSectionProps {
  caseView: CaseView
  onGoToSection: (section: SectionKey, checkId?: string | null) => void
}

/** Issues about one check have a field like "check:<id>:status": the link then opens that check. */
function checkIdOf(issue: ValidationIssue): string | null {
  const match = /^check:([0-9a-f-]{36}):/.exec(issue.field)
  return match ? (match[1] ?? null) : null
}

function IssueList({ issues, tone, onGoTo }: { issues: ValidationIssue[]; tone: 'error' | 'warning'; onGoTo: (s: SectionKey, checkId?: string | null) => void }) {
  return (
    <ul className="flex flex-col gap-2">
      {issues.map((issue) => (
        <li key={`${issue.section}/${issue.field}`}>
          <Alert variant={tone}>
            <div className="flex items-center justify-between gap-3">
              <span>{issue.message}</span>
              {isSectionKey(issue.section) && (
                <Button type="button" size="sm" variant="outline" onClick={() => onGoTo(issue.section as SectionKey, checkIdOf(issue))}>
                  Go to section
                </Button>
              )}
            </div>
          </Alert>
        </li>
      ))}
    </ul>
  )
}

/** Section 8: what still blocks the report (errors) or deserves a second look (warnings). */
export function GenerateSection({ caseView, onGoToSection }: GenerateSectionProps) {
  const caseId = caseView.id
  const validation = useValidation(caseId)
  const result = validation.data

  return (
    <div className="flex flex-col gap-4">
      <div className="border-b border-slate-200 pb-3">
        <h2 className="text-lg font-semibold text-slate-900">8. Generate report</h2>
        <p className="text-sm text-slate-500">Errors must be fixed before a report can be generated or submitted. Warnings can be accepted.</p>
      </div>
      {validation.isLoading && <Spinner />}
      {validation.isError && <Alert variant="error">{describeError(validation.error)}</Alert>}
      {result && (
        <>
          <section aria-label="Errors" className="flex flex-col gap-2">
            <h3 className="text-sm font-semibold text-red-700">Errors ({result.errors.length})</h3>
            {result.errors.length === 0 ? <p className="text-sm text-slate-500">No blocking problems.</p> : <IssueList issues={result.errors} tone="error" onGoTo={onGoToSection} />}
          </section>
          <section aria-label="Warnings" className="flex flex-col gap-2">
            <h3 className="text-sm font-semibold text-amber-700">Warnings ({result.warnings.length})</h3>
            {result.warnings.length === 0 ? <p className="text-sm text-slate-500">Nothing to double-check.</p> : <IssueList issues={result.warnings} tone="warning" onGoTo={onGoToSection} />}
          </section>
        </>
      )}
      <WorkflowPanel caseView={caseView} hasErrors={!result || result.errors.length > 0} warningCount={result?.warnings.length ?? 0} />
      <ReportPanel caseId={caseId} reportId={caseView.reportId} hasErrors={!result || result.errors.length > 0} warningCount={result?.warnings.length ?? 0} />
    </div>
  )
}
