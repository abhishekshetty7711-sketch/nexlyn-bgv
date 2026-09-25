import { useState, type ReactNode } from 'react'
import { ChevronDown } from 'lucide-react'
import { cn } from '@/lib/utils'
import { type CheckView, STATUS_LABELS, STATUS_MARKS } from '../checks/types'
import type { Progress, SectionKey, SectionState } from '../types'
import { SECTION_KEYS } from '../types'

const MARKS: Record<SectionState, { symbol: string; label: string; className: string }> = {
  NOT_STARTED: { symbol: '○', label: 'not started', className: 'text-slate-500' },
  SAVED: { symbol: '●', label: 'saved', className: 'text-emerald-700' },
  WARNING: { symbol: '⚠', label: 'needs attention', className: 'text-amber-700' },
}

const NUMBERS: Record<SectionKey, string> = {
  'report-info': '1',
  candidate: '2',
  'verification-period': '3',
  checks: '4',
  overview: '5',
  remarks: '6',
  settings: '7',
  generate: '8',
}

interface SectionNavigatorProps {
  progress: Progress | undefined
  current: SectionKey
  onSelect: (section: SectionKey) => void
  /** The checks of the case: listed under "4 Checks" so one can be opened directly. */
  checks?: CheckView[]
  currentCheckId?: string | null
  onSelectCheck?: (checkId: string) => void
  /** Shown under the list and folded away with it on a narrow screen (who the case is assigned to). */
  children?: ReactNode
}

/**
 * The left-hand list of sections with a saved / needs-attention mark each, and the overall progress bar. On a narrow
 * screen the list (about 800 px tall) would push the form off the first screen, so it is folded into one button that
 * says where you are ("Section 4 of 8: Checks"); the progress bar stays. From the lg breakpoint the list is always open.
 */
export function SectionNavigator({ progress, current, onSelect, checks = [], currentCheckId = null, onSelectCheck, children }: SectionNavigatorProps) {
  const byKey = new Map(progress?.sections.map((section) => [section.key, section]))
  const percent = progress?.percent ?? 0
  const [open, setOpen] = useState(false) // only matters below lg: from lg up the list is always shown
  const currentLabel = byKey.get(current)?.label ?? current

  const choose = (action: () => void) => {
    setOpen(false) // choosing something on a phone puts the form back in view
    action()
  }

  return (
    <div className="flex flex-col gap-3">
      <div>
        <div className="mb-1 flex justify-between text-xs font-medium text-slate-700">
          <span>Progress</span>
          <span>{percent}%</span>
        </div>
        <div
          role="progressbar"
          aria-label="Case progress"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={percent}
          className="h-2 overflow-hidden rounded-full bg-slate-200"
        >
          <div className="h-full bg-brand-700 transition-all" style={{ width: `${percent}%` }} />
        </div>
      </div>

      <button
        type="button"
        aria-expanded={open}
        aria-controls="case-sections-panel"
        onClick={() => setOpen((value) => !value)}
        className="flex min-h-11 w-full cursor-pointer items-center justify-between gap-2 rounded-md border border-field bg-white px-3 text-left text-sm font-medium text-slate-900 hover:bg-slate-50 lg:hidden"
      >
        <span>
          Section {NUMBERS[current]} of {SECTION_KEYS.length}: {currentLabel}
        </span>
        <ChevronDown className={cn('h-4 w-4 shrink-0 transition-transform', open && 'rotate-180')} aria-hidden />
      </button>

      <div id="case-sections-panel" className={cn('flex flex-col gap-3', !open && 'max-lg:hidden')}>
        <nav aria-label="Case sections">
          <ul className="flex flex-col gap-1">
            {SECTION_KEYS.map((key) => {
              const section = byKey.get(key)
              const mark = MARKS[section?.state ?? 'NOT_STARTED']
              return (
                <li key={key}>
                  <button
                    type="button"
                    aria-current={key === current ? 'page' : undefined}
                    onClick={() => choose(() => onSelect(key))}
                    className={cn(
                      'flex w-full cursor-pointer items-center gap-2 rounded-md px-3 py-2.5 text-left text-sm text-slate-800 hover:bg-slate-100',
                      key === current && 'bg-brand-800 font-medium text-white hover:bg-brand-800',
                    )}
                  >
                    <span className={cn('w-4 text-center', key === current ? 'text-white' : mark.className)} role="img" aria-label={mark.label}>
                      {mark.symbol}
                    </span>
                    <span className="flex-1">
                      {NUMBERS[key]} {section?.label ?? key}
                    </span>
                    {section && section.issues > 0 && key !== 'generate' && (
                      <span className={cn('rounded-full px-1.5 text-xs font-medium', key === current ? 'bg-white text-amber-900' : 'bg-amber-100 text-amber-900')} aria-label={`${section.issues} things to check`}>
                        {section.issues}
                      </span>
                    )}
                  </button>
                  {key === 'checks' && checks.length > 0 && (
                    <ul className="ml-6 mt-1 flex flex-col gap-0.5" aria-label="Checks">
                      {checks.map((check) => (
                        <li key={check.id}>
                          <button
                            type="button"
                            aria-current={current === 'checks' && check.id === currentCheckId ? 'true' : undefined}
                            onClick={() => choose(() => onSelectCheck?.(check.id))}
                            className={cn(
                              'flex min-h-7 w-full cursor-pointer items-center gap-2 rounded px-2 py-1.5 text-left text-xs text-slate-800 hover:bg-slate-100',
                              current === 'checks' && check.id === currentCheckId && 'bg-brand-100 font-semibold text-brand-800 hover:bg-brand-100',
                            )}
                          >
                            <span role="img" aria-label={STATUS_LABELS[check.status]}>
                              {STATUS_MARKS[check.status]}
                            </span>
                            <span className="flex-1 truncate">{check.title}</span>
                          </button>
                        </li>
                      ))}
                    </ul>
                  )}
                </li>
              )
            })}
          </ul>
        </nav>
        {children}
      </div>
    </div>
  )
}
