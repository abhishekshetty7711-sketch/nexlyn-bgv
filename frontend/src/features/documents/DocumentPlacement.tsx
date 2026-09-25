import { cn } from '@/lib/utils'
import type { DocumentView } from './types'

/** What the report does with a document; a change is saved at once by the caller. */
export interface PlacementChange {
  moveToNextPage: boolean
}

interface DocumentPlacementProps {
  document: DocumentView
  /** Saving is in progress: both switches wait, so two quick clicks cannot overwrite each other. */
  busy: boolean
  onChange: (change: PlacementChange) => void
}

/**
 * The page-placement switches of one supporting document (CLAUDE.md 6.2, ported from the reference tool's
 * "Move to Next Page" button). The words change with the state, as in the reference, so colour is never the only signal.
 */
export function DocumentPlacement({ document, busy, onChange }: DocumentPlacementProps) {
  const name = document.displayLabel
  return (
    <div className="flex basis-full flex-col gap-2 border-t border-slate-100 pt-2" role="group" aria-label={`Page options for ${name}`}>
      <div>
        <PlacementSwitch
          on={document.moveToNextPage}
          busy={busy}
          onLabel="✓ On Next Page"
          offLabel="→ Move to Next Page"
          ariaLabel={`Move ${name} to the next page`}
          onClick={() => onChange({ moveToNextPage: !document.moveToNextPage })}
        />
        <p className="mt-1 text-xs text-slate-500">
          Puts this document on a page of its own, straight after the check&apos;s details, in the standard box. The documents that stay are numbered first.
        </p>
      </div>
    </div>
  )
}

interface PlacementSwitchProps {
  on: boolean
  busy: boolean
  onLabel: string
  offLabel: string
  ariaLabel: string
  onClick: () => void
}

function PlacementSwitch({ on, busy, onLabel, offLabel, ariaLabel, onClick }: PlacementSwitchProps) {
  return (
    <button
      type="button"
      aria-pressed={on}
      aria-label={ariaLabel}
      disabled={busy}
      onClick={onClick}
      className={cn(
        'inline-flex min-h-8 cursor-pointer items-center rounded-md border px-3 text-xs font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-600 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50',
        on ? 'border-emerald-300 bg-emerald-100 text-emerald-800' : 'border-slate-200 bg-brand-50 text-brand-800 hover:bg-brand-100',
      )}
    >
      {on ? onLabel : offLabel}
    </button>
  )
}
