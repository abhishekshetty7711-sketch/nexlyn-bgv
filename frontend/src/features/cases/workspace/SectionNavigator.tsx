import { cn } from '@/lib/utils'
import type { Progress, SectionKey, SectionState } from '../types'
import { SECTION_KEYS } from '../types'

const MARKS: Record<SectionState, { symbol: string; label: string; className: string }> = {
  NOT_STARTED: { symbol: '○', label: 'not started', className: 'text-slate-400' },
  SAVED: { symbol: '●', label: 'saved', className: 'text-emerald-600' },
  WARNING: { symbol: '⚠', label: 'needs attention', className: 'text-amber-600' },
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
}

/** The left-hand list of sections with a saved / needs-attention mark each, and the overall progress bar. */
export function SectionNavigator({ progress, current, onSelect }: SectionNavigatorProps) {
  const byKey = new Map(progress?.sections.map((section) => [section.key, section]))
  const percent = progress?.percent ?? 0

  return (
    <nav aria-label="Case sections" className="flex flex-col gap-3">
      <div>
        <div className="mb-1 flex justify-between text-xs text-slate-500">
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
          <div className="h-full bg-emerald-500 transition-all" style={{ width: `${percent}%` }} />
        </div>
      </div>
      <ul className="flex flex-col gap-1">
        {SECTION_KEYS.map((key) => {
          const section = byKey.get(key)
          const mark = MARKS[section?.state ?? 'NOT_STARTED']
          return (
            <li key={key}>
              <button
                type="button"
                aria-current={key === current ? 'page' : undefined}
                onClick={() => onSelect(key)}
                className={cn(
                  'flex w-full items-center gap-2 rounded-md px-3 py-2 text-left text-sm hover:bg-slate-100',
                  key === current && 'bg-slate-900 text-white hover:bg-slate-900',
                )}
              >
                <span className={cn('w-4 text-center', key === current ? 'text-white' : mark.className)} role="img" aria-label={mark.label}>
                  {mark.symbol}
                </span>
                <span className="flex-1">
                  {NUMBERS[key]} {section?.label ?? key}
                </span>
                {section && section.issues > 0 && key !== 'generate' && (
                  <span className="rounded-full bg-amber-100 px-1.5 text-xs text-amber-800" aria-label={`${section.issues} things to check`}>
                    {section.issues}
                  </span>
                )}
              </button>
            </li>
          )
        })}
      </ul>
    </nav>
  )
}
