import { useState } from 'react'
import { describeError } from '@/api/errors'
import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { Spinner } from '@/components/ui/spinner'
import { useAuth } from '@/features/auth/AuthContext'
import { AddCheckDialog } from '../checks/AddCheckDialog'
import { useCheckTypes, useChecks, useDeleteCheck, useReorderChecks } from '../checks/api'
import { CheckEditor } from '../checks/CheckEditor'
import { type CheckStatus, type CheckView, STATUS_LABELS, STATUS_MARKS } from '../checks/types'
import type { CaseView } from '../types'

const STATUS_TONES: Record<CheckStatus, 'green' | 'red' | 'amber' | 'neutral'> = {
  VERIFIED: 'green',
  DISCREPANCY: 'red',
  UNABLE_TO_VERIFY: 'amber',
  CLOSED: 'neutral',
  PENDING: 'amber',
  IN_PROGRESS: 'neutral',
}

interface ChecksSectionProps {
  caseView: CaseView
  /** The check being edited (from the address bar), or null for the list only. */
  checkId: string | null
  onSelectCheck: (checkId: string | null) => void
}

/** Section 4: the case's checks. A list to add / reorder / remove, and one editor for the chosen check. */
export function ChecksSection({ caseView, checkId, onSelectCheck }: ChecksSectionProps) {
  const { hasPermission } = useAuth()
  const checks = useChecks(caseView.id)
  const types = useCheckTypes()
  const reorder = useReorderChecks(caseView.id)
  const remove = useDeleteCheck(caseView.id)
  const [adding, setAdding] = useState(false)
  const [deleting, setDeleting] = useState<CheckView | null>(null)
  const [error, setError] = useState<string | null>(null)

  const canEdit = caseView.editable && hasPermission('CHECK_UPDATE')
  const list = checks.data ?? []
  const selected = list.find((check) => check.id === checkId)
  const def = selected ? types.data?.find((type) => type.code === selected.type) : undefined

  async function move(index: number, by: -1 | 1) {
    const ids = list.map((check) => check.id)
    const target = index + by
    if (target < 0 || target >= ids.length) {
      return
    }
    ;[ids[index], ids[target]] = [ids[target]!, ids[index]!]
    setError(null)
    try {
      await reorder.mutateAsync(ids)
    } catch (problem) {
      setError(describeError(problem))
    }
  }

  async function confirmDelete() {
    if (!deleting) {
      return
    }
    setError(null)
    try {
      await remove.mutateAsync(deleting.id)
      if (deleting.id === checkId) {
        onSelectCheck(null)
      }
    } catch (problem) {
      setError(describeError(problem))
    }
    setDeleting(null)
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-start justify-between gap-4 border-b border-slate-200 pb-3">
        <div>
          <h2 className="text-lg font-semibold text-slate-900">4. Checks</h2>
          <p className="text-sm text-slate-500">One card per verification: identity, address, education, employment, court and more.</p>
        </div>
        {canEdit && <Button onClick={() => setAdding(true)}>Add check</Button>}
      </div>

      {error && <Alert variant="error">{error}</Alert>}
      {checks.isLoading && <Spinner />}
      {checks.isError && <Alert variant="error">{describeError(checks.error)}</Alert>}
      {checks.isSuccess && list.length === 0 && (
        <Alert variant="info">No checks yet. A case needs at least one before it can be submitted.{canEdit ? ' Use "Add check" to start.' : ''}</Alert>
      )}

      {list.length > 0 && (
        <ol className="flex flex-col gap-2" aria-label="Checks on this case">
          {list.map((check, index) => (
            <li key={check.id} className={`flex flex-wrap items-center gap-2 rounded-md border p-2 ${check.id === checkId ? 'border-slate-900 bg-slate-50' : 'border-slate-200'}`}>
              <span className="w-6 text-center text-sm text-slate-500">{index + 1}</span>
              <button type="button" className="flex-1 text-left text-sm font-medium text-slate-900 hover:underline" onClick={() => onSelectCheck(check.id)}>
                {check.title}
              </button>
              <Badge tone={STATUS_TONES[check.status]}>
                {STATUS_MARKS[check.status]} {STATUS_LABELS[check.status]}
              </Badge>
              {canEdit && (
                <>
                  <Button size="sm" variant="ghost" disabled={index === 0 || reorder.isPending} onClick={() => move(index, -1)} aria-label={`Move ${check.title} up`}>
                    ↑
                  </Button>
                  <Button size="sm" variant="ghost" disabled={index === list.length - 1 || reorder.isPending} onClick={() => move(index, 1)} aria-label={`Move ${check.title} down`}>
                    ↓
                  </Button>
                  <Button size="sm" variant="ghost" onClick={() => setDeleting(check)} aria-label={`Remove ${check.title}`}>
                    Remove
                  </Button>
                </>
              )}
            </li>
          ))}
        </ol>
      )}

      {list.length > 0 && !selected && <p className="text-sm text-slate-500">Choose a check above to edit it.</p>}
      {checkId && checks.isSuccess && !selected && <Alert variant="warning">That check no longer exists.</Alert>}

      {selected && def && (
        <div className="border-t border-slate-200 pt-4">
          <CheckEditor
            key={selected.id}
            caseView={caseView}
            check={selected}
            def={def}
            canEdit={canEdit}
            dateFormat={caseView.settings.dateFormat}
          />
        </div>
      )}
      {selected && !def && types.isSuccess && <Alert variant="error">This check&apos;s type is no longer defined on the server.</Alert>}

      {adding && (
        <AddCheckDialog
          caseId={caseView.id}
          clientId={caseView.client.id}
          onClose={() => setAdding(false)}
          onAdded={(check) => {
            setAdding(false)
            onSelectCheck(check.id)
          }}
        />
      )}
      {deleting && (
        <Dialog title="Remove this check?" onClose={() => setDeleting(null)}>
          <p className="mb-4 text-sm text-slate-600">
            &quot;{deleting.title}&quot; and everything entered in it will be removed from this case. This cannot be undone.
          </p>
          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={() => setDeleting(null)}>
              Keep it
            </Button>
            <Button onClick={confirmDelete} disabled={remove.isPending}>
              Remove check
            </Button>
          </div>
        </Dialog>
      )}
    </div>
  )
}
