import { useState } from 'react'
import { describeError } from '@/api/errors'
import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { useUpdateDocument } from './api'
import { isWholePicture } from './crop'
import { CropEditor } from './CropEditor'
import type { Crop, DocumentView } from './types'

interface DocumentEditorDialogProps {
  caseId: string
  checkId: string
  document: DocumentView
  onClose: () => void
}

/** How one supporting document appears on the report: its label, its page placement, and the part shown. */
export function DocumentEditorDialog({ caseId, checkId, document, onClose }: DocumentEditorDialogProps) {
  const update = useUpdateDocument(caseId, checkId)
  const isImage = document.mimeType.startsWith('image/')
  const [label, setLabel] = useState(document.label ?? '')
  const [moveToNextPage, setMoveToNextPage] = useState(document.moveToNextPage)
  const [useLargerBox, setUseLargerBox] = useState(document.useLargerBox)
  const [crop, setCrop] = useState<Crop | null>(document.crop)
  const [error, setError] = useState<string | null>(null)

  async function save() {
    setError(null)
    try {
      await update.mutateAsync({
        id: document.id,
        changes: {
          label: label.trim() || null,
          moveToNextPage,
          useLargerBox: isImage && useLargerBox,
          crop: isImage && crop && !isWholePicture(crop) ? crop : null,
          version: document.version,
        },
      })
      onClose()
    } catch (problem) {
      setError(describeError(problem))
    }
  }

  return (
    <Dialog title={`Edit ${document.displayLabel}`} onClose={onClose}>
      <div className="flex flex-col gap-4">
        {error && <Alert variant="error">{error}</Alert>}
        <Field label="Label" htmlFor="doc-label" hint={`Leave blank to use the numbered name. Currently: ${document.displayLabel}.`}>
          <Input id="doc-label" maxLength={100} value={label} onChange={(event) => setLabel(event.target.value)} />
        </Field>
        <label className="flex items-center gap-2 text-sm text-slate-700">
          <input type="checkbox" checked={moveToNextPage} onChange={(event) => setMoveToNextPage(event.target.checked)} />
          Show this document on a new page
        </label>
        {isImage && (
          <>
            <label className="flex items-center gap-2 text-sm text-slate-700">
              <input type="checkbox" checked={useLargerBox} onChange={(event) => setUseLargerBox(event.target.checked)} />
              Use a larger box (nearly a full page)
            </label>
            <CropEditor documentId={document.id} crop={crop} onChange={setCrop} />
          </>
        )}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button type="button" onClick={save} disabled={update.isPending}>
            {update.isPending ? 'Saving...' : 'Save'}
          </Button>
        </div>
      </div>
    </Dialog>
  )
}
