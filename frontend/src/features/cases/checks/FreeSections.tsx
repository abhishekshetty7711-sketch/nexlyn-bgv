import { useRef, useState } from 'react'
import { describeError } from '@/api/errors'
import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { DocumentImage } from '../../documents/DocumentImage'
import { ACCEPT_PICTURES, problemWithFile } from '../../documents/files'
import { useAddFreeImage, useAddFreeSection, useDeleteFreeSection, useUpdateFreeSection } from './api'
import type { CheckFreeSectionView, CheckView } from './types'

/**
 * Free blocks shown on this check's page of the report: text, or a picture. Each block saves on its
 * own (they are not part of the check form).
 */
export function FreeSections({ caseId, check, canEdit }: { caseId: string; check: CheckView; canEdit: boolean }) {
  const add = useAddFreeSection(caseId, check.id)
  const addImage = useAddFreeImage(caseId, check.id)
  const picker = useRef<HTMLInputElement>(null)
  const [error, setError] = useState<string | null>(null)
  const [draft, setDraft] = useState<string | null>(null)
  const blocks = [...check.freeSections].sort((a, b) => a.sortOrder - b.sortOrder)

  async function addBlock() {
    setError(null)
    try {
      await add.mutateAsync((draft ?? '').trim())
      setDraft(null)
    } catch (problem) {
      setError(describeError(problem))
    }
  }

  async function addPicture(files: FileList | null) {
    const file = files?.[0]
    if (!file) {
      return
    }
    const problem = problemWithFile(file, false)
    if (problem) {
      setError(problem)
      return
    }
    setError(null)
    try {
      await addImage.mutateAsync(file)
    } catch (failure) {
      setError(describeError(failure))
    }
    if (picker.current) {
      picker.current.value = ''
    }
  }

  return (
    <section className="flex flex-col gap-3 border-t border-slate-200 pt-4" aria-label="Free blocks">
      <div className="flex items-center justify-between gap-2">
        <h4 className="text-sm font-semibold text-slate-800">Free blocks (text or picture)</h4>
        {canEdit && (
          <div className="flex gap-2">
            <Button type="button" size="sm" variant="outline" disabled={draft !== null} onClick={() => setDraft('')}>
              Add text block
            </Button>
            <input ref={picker} type="file" accept={ACCEPT_PICTURES} className="sr-only" aria-label="Choose a picture for a block" onChange={(event) => void addPicture(event.target.files)} />
            <Button type="button" size="sm" variant="outline" disabled={addImage.isPending} onClick={() => picker.current?.click()}>
              {addImage.isPending ? 'Uploading...' : 'Add picture block'}
            </Button>
          </div>
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
      {blocks.length === 0 && draft === null && <p className="text-sm text-slate-500">No extra blocks on this check.</p>}
      {blocks.map((section, index) =>
        section.kind === 'IMAGE' ? (
          <PictureBlock key={section.id} caseId={caseId} checkId={check.id} section={section} index={index} canEdit={canEdit} />
        ) : (
          <TextBlock key={section.id} caseId={caseId} checkId={check.id} section={section} index={index} canEdit={canEdit} />
        ),
      )}
    </section>
  )
}

interface BlockProps {
  caseId: string
  checkId: string
  section: CheckFreeSectionView
  /** Position among all blocks, for the label. */
  index: number
  canEdit: boolean
}

function PictureBlock({ caseId, checkId, section, index, canEdit }: BlockProps) {
  const remove = useDeleteFreeSection(caseId, checkId)
  const [error, setError] = useState<string | null>(null)

  async function removeBlock() {
    setError(null)
    try {
      await remove.mutateAsync(section.id)
    } catch (problem) {
      setError(describeError(problem))
    }
  }

  return (
    <div className="flex flex-col gap-2 rounded-md border border-slate-200 p-3">
      <span className="text-sm font-medium text-slate-700">Picture block {index + 1}</span>
      {section.documentId && <DocumentImage documentId={section.documentId} alt={`Picture block ${index + 1}`} className="max-h-48 w-auto self-start rounded border border-slate-200" />}
      {error && (
        <p className="text-xs text-red-600" role="alert">
          {error}
        </p>
      )}
      {canEdit && (
        <div>
          <Button type="button" size="sm" variant="outline" disabled={remove.isPending} onClick={() => void removeBlock()} aria-label={`Delete picture block ${index + 1}`}>
            Delete block
          </Button>
        </div>
      )}
    </div>
  )
}

function TextBlock({ caseId, checkId, section, index, canEdit }: BlockProps) {
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
