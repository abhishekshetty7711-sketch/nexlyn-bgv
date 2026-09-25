import type { FormEventHandler, ReactNode } from 'react'
import { CheckCircle2 } from 'lucide-react'
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
      {/* The Save button stays in view while a long section scrolls (below the slim bar on narrow screens). */}
      <div className="sticky top-13 z-10 -mx-4 -mt-4 flex items-start justify-between gap-4 rounded-t-xl border-b border-line bg-white/95 px-4 py-3 backdrop-blur lg:top-0">
        <div>
          <h2 className="text-lg font-semibold text-slate-900">{title}</h2>
          {description && <p className="text-sm text-slate-600">{description}</p>}
        </div>
        {canEdit && (
          <div className="flex items-center gap-3">
            {saving.justSaved && (
              <span role="status" className="flex items-center gap-1 text-sm font-medium text-emerald-700">
                <CheckCircle2 className="h-4 w-4" aria-hidden />
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
