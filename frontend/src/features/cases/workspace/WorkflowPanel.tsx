import { useState } from 'react'
import { describeError } from '@/api/errors'
import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { useFinalizeReport, useReportVersions } from '../../reports/api'
import { useHistory, useWorkflowStep, type WorkflowStep } from '../api'
import { formatDateTime } from '../format'
import type { CaseView, HistoryEntry } from '../types'
import { LIFECYCLE_LABELS } from '../types'

type Dialogs = null | 'submit' | 'approve' | 'changes' | 'finalize' | 'reopen'

interface WorkflowPanelProps {
  caseView: CaseView
  /** The checklist has errors: the case cannot be submitted yet. */
  hasErrors: boolean
  warningCount: number
}

const ACTION_TEXT: Record<HistoryEntry['action'], string> = {
  SUBMIT: 'sent the case for review',
  APPROVE: 'approved the case',
  REQUEST_CHANGES: 'sent the case back for changes',
  FINALIZE: 'finalized the report',
  REOPEN: 'reopened the case',
}

/**
 * Section 8, the review steps (CLAUDE.md sections 9.2 and 11.3). What each admin is offered comes from the
 * server (`workflow.actions`), which applies the same rules as on every call, including "whoever prepared a
 * case can never approve or finalize it".
 */
export function WorkflowPanel({ caseView, hasErrors, warningCount }: WorkflowPanelProps) {
  const step = useWorkflowStep(caseView.id)
  const history = useHistory(caseView.id)
  const [dialog, setDialog] = useState<Dialogs>(null)
  const [problem, setProblem] = useState<string | null>(null)
  const { actions } = caseView.workflow
  const anyAction = Object.values(actions).some(Boolean)

  async function run(which: WorkflowStep, body?: Record<string, unknown>): Promise<boolean> {
    setProblem(null)
    try {
      await step.mutateAsync({ step: which, body })
      setDialog(null)
      return true
    } catch (error) {
      setProblem(describeError(error))
      return false
    }
  }

  const workflow = caseView.workflow

  return (
    <section className="flex flex-col gap-3 border-t border-slate-200 pt-4" aria-label="Review">
      <div className="flex flex-wrap items-center gap-2">
        <h3 className="text-sm font-semibold text-slate-800">Review</h3>
        <Badge tone={caseView.lifecycle === 'APPROVED' || caseView.lifecycle === 'FINALIZED' ? 'green' : caseView.lifecycle === 'CHANGES_REQUESTED' ? 'amber' : 'neutral'}>
          {LIFECYCLE_LABELS[caseView.lifecycle]}
        </Badge>
      </div>

      <ul className="text-sm text-slate-600">
        {workflow.submittedAt && (
          <li>
            Sent for review by {workflow.submittedByName} on {formatDateTime(workflow.submittedAt)}.
          </li>
        )}
        {workflow.approvedAt && (
          <li>
            Approved by {workflow.reviewedByName} on {formatDateTime(workflow.approvedAt)}.
          </li>
        )}
        {workflow.finalizedAt && (
          <li>
            Finalized by {workflow.finalizedByName} on {formatDateTime(workflow.finalizedAt)}.
          </li>
        )}
      </ul>
      {caseView.reviewComment && <Alert variant={caseView.lifecycle === 'CHANGES_REQUESTED' ? 'warning' : 'info'}>{caseView.reviewComment}</Alert>}
      {!anyAction && caseView.lifecycle === 'IN_REVIEW' && <p className="text-sm text-slate-500">Waiting for a reviewer. Whoever prepared or submitted a case cannot approve it.</p>}
      {problem && dialog === null && <Alert variant="error">{problem}</Alert>}

      <div className="flex flex-wrap gap-2">
        {actions.canSubmit && (
          <Button type="button" disabled={hasErrors} onClick={() => setDialog('submit')}>
            Submit for review
          </Button>
        )}
        {actions.canApprove && (
          <Button type="button" onClick={() => setDialog('approve')}>
            Approve
          </Button>
        )}
        {actions.canRequestChanges && (
          <Button type="button" variant="outline" onClick={() => setDialog('changes')}>
            Request changes
          </Button>
        )}
        {actions.canFinalize && (
          <Button type="button" onClick={() => setDialog('finalize')}>
            Finalize report
          </Button>
        )}
        {actions.canReopen && (
          <Button type="button" variant="outline" onClick={() => setDialog('reopen')}>
            Reopen for changes
          </Button>
        )}
      </div>
      {actions.canSubmit && hasErrors && <p className="text-xs text-slate-500">Fix the errors above to submit the case.</p>}

      <HistoryList entries={history.data ?? []} />

      {dialog === 'submit' && (
        <ConfirmDialog
          title="Submit for review?"
          confirmLabel={warningCount > 0 ? 'Submit anyway' : 'Submit'}
          onClose={() => setDialog(null)}
          onConfirm={() => run('submit-review', { acknowledgeWarnings: true })}
          problem={problem}
          busy={step.isPending}
        >
          <p className="text-sm text-slate-600">
            The case will be locked while it is reviewed: nobody can change it until it is approved or sent back.
            {warningCount > 0 && ` It has ${warningCount} warning${warningCount === 1 ? '' : 's'} (see the list above); you can still submit it.`}
          </p>
        </ConfirmDialog>
      )}
      {dialog === 'approve' && (
        <CommentDialog
          title="Approve this case?"
          label="Comment (optional)"
          required={false}
          confirmLabel="Approve"
          onClose={() => setDialog(null)}
          onConfirm={(text) => run('approve', { comment: text })}
          problem={problem}
          busy={step.isPending}
        />
      )}
      {dialog === 'changes' && (
        <CommentDialog
          title="Send back for changes"
          label="What needs to change?"
          required
          confirmLabel="Send back"
          onClose={() => setDialog(null)}
          onConfirm={(text) => run('request-changes', { comment: text })}
          problem={problem}
          busy={step.isPending}
        />
      )}
      {dialog === 'reopen' && (
        <CommentDialog
          title="Reopen this case?"
          label="Why is it reopened?"
          required
          confirmLabel="Reopen"
          hint="The final report stays as it is. The next report is a new version, and the case has to be reviewed and approved again."
          onClose={() => setDialog(null)}
          onConfirm={(text) => run('reopen', { reason: text })}
          problem={problem}
          busy={step.isPending}
        />
      )}
      {dialog === 'finalize' && <FinalizeDialog caseView={caseView} onClose={() => setDialog(null)} />}
    </section>
  )
}

function HistoryList({ entries }: { entries: HistoryEntry[] }) {
  if (entries.length === 0) {
    return null
  }
  return (
    <details className="text-sm text-slate-600">
      <summary className="cursor-pointer py-1 text-sm font-medium text-slate-700">History ({entries.length})</summary>
      <ol className="mt-2 flex flex-col gap-1" aria-label="Case history">
        {entries.map((entry, index) => (
          <li key={`${entry.at}-${index}`}>
            <span className="text-slate-500">{formatDateTime(entry.at)}</span> · {entry.actorName} {ACTION_TEXT[entry.action]}
            {entry.reportVersion ? ` (report v${entry.reportVersion})` : ''}
            {entry.comment ? `: "${entry.comment}"` : ''}
          </li>
        ))}
      </ol>
    </details>
  )
}

interface DialogFrameProps {
  title: string
  onClose: () => void
  problem: string | null
  children: React.ReactNode
  footer: React.ReactNode
}

function DialogFrame({ title, onClose, problem, children, footer }: DialogFrameProps) {
  return (
    <Dialog title={title} onClose={onClose}>
      <div className="flex flex-col gap-3">
        {problem && <Alert variant="error">{problem}</Alert>}
        {children}
        <div className="flex justify-end gap-2">{footer}</div>
      </div>
    </Dialog>
  )
}

function ConfirmDialog(props: { title: string; confirmLabel: string; onClose: () => void; onConfirm: () => void; problem: string | null; busy: boolean; children: React.ReactNode }) {
  return (
    <DialogFrame
      title={props.title}
      onClose={props.onClose}
      problem={props.problem}
      footer={
        <>
          <Button variant="outline" onClick={props.onClose}>
            Go back
          </Button>
          <Button onClick={props.onConfirm} disabled={props.busy}>
            {props.confirmLabel}
          </Button>
        </>
      }
    >
      {props.children}
    </DialogFrame>
  )
}

function CommentDialog(props: {
  title: string
  label: string
  required: boolean
  confirmLabel: string
  hint?: string
  onClose: () => void
  onConfirm: (text: string) => void
  problem: string | null
  busy: boolean
}) {
  const [text, setText] = useState('')
  return (
    <DialogFrame
      title={props.title}
      onClose={props.onClose}
      problem={props.problem}
      footer={
        <>
          <Button variant="outline" onClick={props.onClose}>
            Go back
          </Button>
          <Button onClick={() => props.onConfirm(text.trim())} disabled={props.busy || (props.required && text.trim() === '')}>
            {props.confirmLabel}
          </Button>
        </>
      }
    >
      {props.hint && <p className="text-sm text-slate-600">{props.hint}</p>}
      <Field label={props.label} htmlFor="wf-comment">
        <Textarea id="wf-comment" rows={4} maxLength={2000} value={text} onChange={(event) => setText(event.target.value)} />
      </Field>
    </DialogFrame>
  )
}

/** Choose the draft to finalize (one made after the approval) and, if wanted, a password to open the file. */
function FinalizeDialog({ caseView, onClose }: { caseView: CaseView; onClose: () => void }) {
  const versions = useReportVersions(caseView.id)
  const finalize = useFinalizeReport(caseView.id)
  const approvedAt = caseView.workflow.approvedAt
  const eligible = (versions.data ?? []).filter((v) => v.kind === 'DRAFT' && (!approvedAt || new Date(v.generatedAt) >= new Date(approvedAt)))
  const [chosen, setChosen] = useState<number | null>(null)
  const [password, setPassword] = useState('')
  const [repeat, setRepeat] = useState('')
  const [problem, setProblem] = useState<string | null>(null)

  const version = chosen ?? eligible[0]?.version ?? null
  const passwordProblem =
    password !== '' && (password.length < 8 || password.length > 128)
      ? 'Use 8 to 128 characters, or leave the password empty.'
      : password !== repeat
        ? 'The two passwords are not the same.'
        : null

  async function confirm() {
    if (version === null) {
      return
    }
    setProblem(null)
    try {
      await finalize.mutateAsync({ version, openPassword: password === '' ? null : password })
      onClose()
    } catch (error) {
      setProblem(describeError(error))
    }
  }

  return (
    <DialogFrame
      title="Finalize the report"
      onClose={onClose}
      problem={problem}
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            Go back
          </Button>
          <Button onClick={() => void confirm()} disabled={finalize.isPending || version === null || passwordProblem !== null}>
            Finalize
          </Button>
        </>
      }
    >
      <p className="text-sm text-slate-600">
        This makes the protected final PDF and locks the case for good (it can only be reopened to make a new version).
      </p>
      {versions.isSuccess && eligible.length === 0 && (
        <Alert variant="warning">There is no draft made after the approval yet. Generate a draft PDF of the approved case, check it, then come back here.</Alert>
      )}
      {eligible.length > 0 && (
        <Field label="Draft to finalize" htmlFor="wf-version" hint="Only drafts made after the approval can be finalized, so the final report shows exactly what was approved.">
          <select
            id="wf-version"
            className="h-9 w-full rounded-md border border-slate-300 bg-white px-3 text-sm"
            value={version ?? ''}
            onChange={(event) => setChosen(Number(event.target.value))}
          >
            {eligible.map((v) => (
              <option key={v.version} value={v.version}>
                Version {v.version} ({v.pageCount} pages, made {formatDateTime(v.generatedAt)})
              </option>
            ))}
          </select>
        </Field>
      )}
      <Field label="Password to open the file (optional)" htmlFor="wf-password" hint="It is not stored and cannot be recovered. Without one, the file opens freely but still cannot be edited.">
        <Input id="wf-password" type="password" autoComplete="new-password" value={password} onChange={(event) => setPassword(event.target.value)} />
      </Field>
      {password !== '' && (
        <Field label="Repeat the password" htmlFor="wf-password2" error={passwordProblem ?? undefined}>
          <Input id="wf-password2" type="password" autoComplete="new-password" value={repeat} onChange={(event) => setRepeat(event.target.value)} />
        </Field>
      )}
    </DialogFrame>
  )
}
