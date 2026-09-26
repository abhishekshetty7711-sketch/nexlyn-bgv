import { useRef, useState } from 'react'
import { describeError } from '@/api/errors'
import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { Spinner } from '@/components/ui/spinner'
import { fetchDocumentBlob, useDeleteDocument, useDocuments, useReorderDocuments, useUpdateDocument, useUploadToCheck } from './api'
import { DocumentEditorDialog } from './DocumentEditorDialog'
import { DocumentImage } from './DocumentImage'
import { DocumentPlacement, type PlacementChange } from './DocumentPlacement'
import { DocumentPreviewDialog } from './DocumentPreviewDialog'
import { ACCEPT_DOCUMENTS, formatBytes, problemWithFile } from './files'
import { type DocumentView, QUALITY_LABELS } from './types'

interface CheckDocumentsProps {
  caseId: string
  checkId: string
  /** May add, describe and reorder documents (DOCUMENT_UPLOAD, and the case is not locked). */
  canUpload: boolean
  /** May remove documents (DOCUMENT_DELETE, and the case is not locked). */
  canDelete: boolean
}

/** Section 4 D: the supporting documents of one check. Every change saves at once. */
export function CheckDocuments({ caseId, checkId, canUpload, canDelete }: CheckDocumentsProps) {
  const documents = useDocuments(checkId)
  const upload = useUploadToCheck(caseId, checkId)
  const reorder = useReorderDocuments(caseId, checkId)
  const remove = useDeleteDocument(caseId, checkId)
  const update = useUpdateDocument(caseId, checkId)
  const picker = useRef<HTMLInputElement>(null)
  const [problems, setProblems] = useState<string[]>([])
  const [uploading, setUploading] = useState(false)
  const [editing, setEditing] = useState<DocumentView | null>(null)
  const [viewing, setViewing] = useState<DocumentView | null>(null)
  const [deleting, setDeleting] = useState<DocumentView | null>(null)

  const supporting = (documents.data ?? []).filter((document) => document.kind === 'CHECK_DOC')

  async function addFiles(files: FileList | null) {
    if (!files || files.length === 0) {
      return
    }
    setProblems([])
    setUploading(true)
    const found: string[] = []
    for (const file of Array.from(files)) {
      const problem = problemWithFile(file, true)
      if (problem) {
        found.push(problem)
        continue
      }
      try {
        await upload.mutateAsync({ file, kind: 'CHECK_DOC' })
      } catch (error) {
        found.push(`${file.name}: ${describeError(error)}`)
      }
    }
    setProblems(found)
    setUploading(false)
    if (picker.current) {
      picker.current.value = ''
    }
  }

  async function move(index: number, by: -1 | 1) {
    const ids = supporting.map((document) => document.id)
    const target = index + by
    if (target < 0 || target >= ids.length) {
      return
    }
    ;[ids[index], ids[target]] = [ids[target]!, ids[index]!]
    try {
      await reorder.mutateAsync(ids)
    } catch (error) {
      setProblems([describeError(error)])
    }
  }

  /** A page switch saves the moment it is clicked; the other settings of the document are sent back unchanged. */
  async function place(document: DocumentView, change: PlacementChange) {
    setProblems([])
    try {
      await update.mutateAsync({
        id: document.id,
        changes: { label: document.label, moveToNextPage: change.moveToNextPage, useLargerBox: change.useLargerBox, crop: document.crop, version: document.version },
      })
    } catch (error) {
      setProblems([describeError(error)])
    }
  }

  async function confirmDelete() {
    if (!deleting) {
      return
    }
    try {
      await remove.mutateAsync(deleting.id)
    } catch (error) {
      setProblems([describeError(error)])
    }
    setDeleting(null)
  }

  return (
    <section className="flex flex-col gap-3 border-t border-slate-200 pt-4" aria-label="Supporting documents">
      <div className="flex items-center justify-between gap-3">
        <h4 className="text-sm font-semibold text-slate-800">Supporting documents</h4>
        {canUpload && (
          <div>
            <input
              ref={picker}
              id={`upload-${checkId}`}
              type="file"
              multiple
              accept={ACCEPT_DOCUMENTS}
              className="sr-only"
              aria-label="Upload documents"
              disabled={uploading}
              onChange={(event) => void addFiles(event.target.files)}
            />
            <Button type="button" size="sm" variant="outline" disabled={uploading} onClick={() => picker.current?.click()}>
              {uploading ? 'Uploading...' : 'Add documents'}
            </Button>
          </div>
        )}
      </div>
      <p className="text-xs text-slate-500">JPEG, PNG or PDF, up to 10 MB each. Pictures are cleaned of location and camera details when stored.</p>

      {problems.length > 0 && (
        <Alert variant="error">
          <ul className="list-disc pl-4">
            {problems.map((problem) => (
              <li key={problem}>{problem}</li>
            ))}
          </ul>
        </Alert>
      )}
      {documents.isLoading && <Spinner />}
      {documents.isError && <Alert variant="error">{describeError(documents.error)}</Alert>}
      {documents.isSuccess && supporting.length === 0 && <p className="text-sm text-slate-500">No documents attached yet. A check without one gets a warning before the report can go out.</p>}

      {supporting.length > 0 && (
        <ol className="flex flex-col gap-2" aria-label="Documents of this check">
          {supporting.map((document, index) => (
            <li key={document.id} className="flex flex-wrap items-center gap-3 rounded-md border border-slate-200 p-2">
              {document.mimeType.startsWith('image/') ? (
                <DocumentImage documentId={document.id} alt={`Preview of ${document.displayLabel}`} className="h-16 w-16 rounded border border-slate-200" />
              ) : (
                <span className="flex h-16 w-16 items-center justify-center rounded border border-slate-200 bg-slate-50 text-xs font-semibold text-slate-600" aria-hidden="true">
                  PDF
                </span>
              )}
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium text-slate-900">{document.displayLabel}</p>
                <p className="truncate text-xs text-slate-500">
                  {document.originalFilename ?? 'unnamed'} · {formatBytes(document.sizeBytes)}
                  {document.width && document.height ? ` · ${document.width}×${document.height}` : ''}
                </p>
                <div className="mt-1 flex flex-wrap gap-1">
                  {document.quality && <Badge tone={document.quality === 'HIGH' ? 'green' : document.quality === 'MEDIUM' ? 'amber' : 'red'}>{QUALITY_LABELS[document.quality]}</Badge>}
                  {document.moveToNextPage && <Badge>New page</Badge>}
                  {document.useLargerBox && <Badge>Larger box</Badge>}
                  {document.crop && <Badge>Cropped</Badge>}
                </div>
              </div>
              <div className="flex flex-wrap items-center gap-1">
                <Button type="button" size="sm" variant="ghost" onClick={() => (document.mimeType === 'application/pdf' ? void openFile(document, setProblems) : setViewing(document))} aria-label={`${document.mimeType === 'application/pdf' ? 'Download' : 'View'} ${document.displayLabel}`}>
                  {document.mimeType === 'application/pdf' ? 'Download' : 'View'}
                </Button>
                {canUpload && (
                  <>
                    <Button type="button" size="sm" variant="ghost" disabled={index === 0 || reorder.isPending} onClick={() => void move(index, -1)} aria-label={`Move ${document.displayLabel} up`}>
                      ↑
                    </Button>
                    <Button type="button" size="sm" variant="ghost" disabled={index === supporting.length - 1 || reorder.isPending} onClick={() => void move(index, 1)} aria-label={`Move ${document.displayLabel} down`}>
                      ↓
                    </Button>
                    <Button type="button" size="sm" variant="ghost" onClick={() => setEditing(document)} aria-label={`Edit ${document.displayLabel}`}>
                      Edit
                    </Button>
                  </>
                )}
                {canDelete && (
                  <Button type="button" size="sm" variant="ghost" onClick={() => setDeleting(document)} aria-label={`Remove ${document.displayLabel}`}>
                    Remove
                  </Button>
                )}
              </div>
              {canUpload && <DocumentPlacement document={document} busy={update.isPending} onChange={(change) => void place(document, change)} />}
            </li>
          ))}
        </ol>
      )}

      {viewing && <DocumentPreviewDialog key={viewing.id} document={viewing} onClose={() => setViewing(null)} onOpenInTab={() => void openFile(viewing, setProblems)} />}
      {editing && <DocumentEditorDialog key={editing.id} caseId={caseId} checkId={checkId} document={editing} onClose={() => setEditing(null)} />}
      {deleting && (
        <Dialog title="Remove this document?" onClose={() => setDeleting(null)}>
          <p className="mb-4 text-sm text-slate-600">&quot;{deleting.displayLabel}&quot; will be removed from this check and the report.</p>
          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={() => setDeleting(null)}>
              Keep it
            </Button>
            <Button onClick={() => void confirmDelete()} disabled={remove.isPending}>
              Remove document
            </Button>
          </div>
        </Dialog>
      )}
    </section>
  )
}

/** Pictures open in a new tab; PDFs are saved as a file (never opened inside this site). */
async function openFile(document: DocumentView, onProblem: (problems: string[]) => void) {
  try {
    const blob = await fetchDocumentBlob(document.id)
    const url = URL.createObjectURL(blob)
    if (document.mimeType === 'application/pdf') {
      const link = window.document.createElement('a')
      link.href = url
      link.download = document.originalFilename ?? 'document.pdf'
      link.click()
    } else {
      window.open(url, '_blank', 'noopener')
    }
    setTimeout(() => URL.revokeObjectURL(url), 60_000)
  } catch (error) {
    onProblem([describeError(error)])
  }
}
