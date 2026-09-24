import { useState } from 'react'
import { describeError } from '@/api/errors'
import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { useAddFreeSection, useDeleteFreeSection, useUpdateFreeSection } from './api'
import type { CheckFreeSectionView, CheckView } from './types'

/**
 * Free text blocks shown on this check's page of the report. Each block saves on its own (they are
 * not part of the check form). Image blocks arrive with the documents phase.
 */
export function FreeSections({ caseId, check, canEdit }: { caseId: string; check: CheckView; canEdit: boolean }) {
  const add = useAddFreeSection(caseId, check.id)
  const [error, setError] = useState<string | null>(null)
  const [draft, setDraft] = useState<string | null>(null)
  const textBlocks = check.freeSections.filter((section) => section.kind === 'TEXT')

  async function addBlock() {
    setError(null)
    try {
      await add.mutateAsync((draft ?? '').trim())
      setDraft(null)
    } catch (problem) {
      setError(describeError(problem))
    }
  }

  return (
    <section className="flex flex-col gap-3 border-t border-slate-200 pt-4" aria-label="Free text blocks">
      <div className="flex items-center justify-between">
        <h4 className="text-sm font-semibold text-slate-800">Free text blocks</h4>
        {canEdit && (
          <Button type="button" size="sm" variant="outline" disabled={draft !== null} onClick={() => setDraft('')}>
            Add text block
          </Button>
        )}
      </div>
      {error && <Alert variant="error">{error}</Alert>}
      {draft !== null && (
        <div className="flex flex-col gap-2 rounded-md border border-dashed border-slate-300 p-3">
          <label className="text-sm font-medium text-slate-700" htmlFor="fs-new">
            New text block
          </label>
          <Textarea id="fs-new" rows={3} value={draft} onChange={(event) => setDraft(event.target.value)} />
          <div className="flex gap-2">
            <Button type="button" size="sm" disabled={draft.trim() === '' || add.isPending} onClick={addBlock}>
              Add block
            </Button>
            <Button type="button" size="sm" variant="outline" onClick={() => setDraft(null)}>
              Cancel
            </Button>
          </div>
        </div>
      )}
      {textBlocks.length === 0 && draft === null && <p className="text-sm text-slate-500">No extra text blocks on this check.</p>}
      {textBlocks.map((section, index) => (
        <TextBlock key={section.id} caseId={caseId} checkId={check.id} section={section} index={index} canEdit={canEdit} />
      ))}
    </section>
  )
}

function TextBlock({ caseId, checkId, section, index, canEdit }: { caseId: string; checkId: string; section: CheckFreeSectionView; index: number; canEdit: boolean }) {
  const update = useUpdateFreeSection(caseId, checkId)
  const remove = useDeleteFreeSection(caseId, checkId)
  const [text, setText] = useState(section.text ?? '')
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)
  const changed = text !== (section.text ?? '')

  async function run(action: () => Promise<unknown>, onDone?: () => void) {
    setError(null)
    try {
      await action()
      onDone?.()
    } catch (problem) {
      setError(describeError(problem))
    }
  }

  return (
    <div className="flex flex-col gap-2 rounded-md border border-slate-200 p-3">
      <label className="text-sm font-medium text-slate-700" htmlFor={`fs-${section.id}`}>
        Text block {index + 1}
      </label>
      <Textarea
        id={`fs-${section.id}`}
        rows={3}
        value={text}
        disabled={!canEdit}
        onChange={(event) => {
          setText(event.target.value)
          setSaved(false)
        }}
      />
      {error && (
        <p className="text-xs text-red-600" role="alert">
          {error}
        </p>
      )}
      {canEdit && (
        <div className="flex items-center gap-2">
          <Button
            type="button"
            size="sm"
            disabled={!changed || text.trim() === '' || update.isPending}
            onClick={() => run(() => update.mutateAsync({ id: section.id, text: text.trim() }), () => setSaved(true))}
            aria-label={`Save text block ${index + 1}`}
          >
            Save block
          </Button>
          <Button type="button" size="sm" variant="outline" disabled={remove.isPending} onClick={() => run(() => remove.mutateAsync(section.id))} aria-label={`Delete text block ${index + 1}`}>
            Delete block
          </Button>
          {saved && (
            <span role="status" className="text-xs text-emerald-700">
              Saved
            </span>
          )}
        </div>
      )}
    </div>
  )
}
