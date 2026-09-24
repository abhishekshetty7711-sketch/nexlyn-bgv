import type { FormEventHandler, ReactNode } from 'react'
import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import type { SectionSaving } from './useSectionSaving'

interface SectionShellProps {
  title: string
  description?: string
  /** False = read-only: no Save button, the fields should be disabled by the section. */
  canEdit: boolean
  saving: SectionSaving
  onSubmit: FormEventHandler<HTMLFormElement>
  onReload: () => void
  /** Called on any edit inside the form (used to hide the "Saved" flash). */
  onEdit?: () => void
  children: ReactNode
}

/** The frame every editable section shares: heading, Save button, saved / error feedback. */
export function SectionShell({ title, description, canEdit, saving, onSubmit, onReload, onEdit, children }: SectionShellProps) {
  return (
    <form className="flex flex-col gap-4" onSubmit={onSubmit} onChange={onEdit} noValidate aria-label={title}>
      <div className="flex items-start justify-between gap-4 border-b border-slate-200 pb-3">
        <div>
          <h2 className="text-lg font-semibold text-slate-900">{title}</h2>
          {description && <p className="text-sm text-slate-500">{description}</p>}
        </div>
        {canEdit && (
          <div className="flex items-center gap-3">
            {saving.justSaved && (
              <span role="status" className="text-sm text-emerald-700">
                Saved
              </span>
            )}
            <Button type="submit" disabled={saving.isSaving}>
              {saving.isSaving ? 'Saving...' : 'Save'}
            </Button>
          </div>
        )}
      </div>
      {saving.errorMessage && (
        <Alert variant="error">
          <div className="flex items-center justify-between gap-3">
            <span>{saving.errorMessage}</span>
            {saving.conflict && (
              <Button type="button" size="sm" variant="outline" onClick={onReload}>
                Reload the case
              </Button>
            )}
          </div>
        </Alert>
      )}
      {children}
    </form>
  )
}
