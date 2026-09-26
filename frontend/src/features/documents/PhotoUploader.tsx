import { useRef, useState } from 'react'
import { describeError } from '@/api/errors'
import { Button } from '@/components/ui/button'
import type { FieldIssue } from '@/components/ui/field'
import { useAuth } from '@/features/auth/AuthContext'
import type { CaseView } from '../cases/types'
import { useDeletePhoto, useUploadPhoto } from './api'
import { DocumentImage } from './DocumentImage'
import { ACCEPT_PICTURES, problemWithFile } from './files'

/**
 * The candidate's photo. It saves at once (separately from the Save button of the details form) and
 * does not change the case's version, so typing in the form is never disturbed.
 */
export function PhotoUploader({ caseView, issue }: { caseView: CaseView; issue?: FieldIssue }) {
  const { hasPermission } = useAuth()
  const upload = useUploadPhoto(caseView.id)
  const remove = useDeletePhoto(caseView.id)
  const picker = useRef<HTMLInputElement>(null)
  const [problem, setProblem] = useState<string | null>(null)

  const canUpload = caseView.editable && hasPermission('DOCUMENT_UPLOAD')
  const canRemove = caseView.editable && hasPermission('DOCUMENT_DELETE')
  const photoId = caseView.candidate.photoDocumentId

  async function choose(files: FileList | null) {
    const file = files?.[0]
    if (!file) {
      return
    }
    setProblem(problemWithFile(file, false))
    if (problemWithFile(file, false)) {
      return
    }
    try {
      await upload.mutateAsync(file)
    } catch (error) {
      setProblem(describeError(error))
    }
    if (picker.current) {
      picker.current.value = ''
    }
  }

  async function removePhoto() {
    setProblem(null)
    try {
      await remove.mutateAsync()
    } catch (error) {
      setProblem(describeError(error))
    }
  }

  return (
    <div className="flex flex-col gap-2" role="group" aria-label="Candidate photo">
      <span className="text-sm font-medium text-slate-700">Photo</span>
      <div className="flex items-center gap-4">
        {photoId ? (
          <DocumentImage documentId={photoId} alt="Candidate photo" className="h-28 w-24 rounded-md border border-slate-300" />
        ) : (
          <span className="flex h-28 w-24 items-center justify-center rounded-md border border-dashed border-slate-300 text-xs text-slate-500">No photo</span>
        )}
        <div className="flex flex-col items-start gap-2">
          {canUpload && (
            <>
              <input ref={picker} type="file" accept={ACCEPT_PICTURES} className="sr-only" aria-label="Choose a photo" onChange={(event) => void choose(event.target.files)} />
              <Button type="button" size="sm" variant="outline" disabled={upload.isPending} onClick={() => picker.current?.click()}>
                {upload.isPending ? 'Uploading...' : photoId ? 'Replace photo' : 'Upload photo'}
              </Button>
            </>
          )}
          {photoId && canRemove && (
            <Button type="button" size="sm" variant="ghost" disabled={remove.isPending} onClick={() => void removePhoto()}>
              Remove photo
            </Button>
          )}
          <p className="text-xs text-slate-500">JPEG or PNG, up to 10 MB. Saved as soon as it is uploaded.</p>
        </div>
      </div>
      {issue && !photoId && !problem && (
        <p className={`text-xs font-medium ${issue.level === 'error' ? 'text-red-700' : 'text-amber-800'}`}>{issue.message}</p>
      )}
      {problem && (
        <p className="text-xs text-red-600" role="alert">
          {problem}
        </p>
      )}
    </div>
  )
}
