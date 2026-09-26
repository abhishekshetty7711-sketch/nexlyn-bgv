import { cn } from '@/lib/utils'
import { type CheckStatus, STATUS_LABELS, STATUS_MARKS } from '../checks/types'
import type { Progress } from '../types'

/** The order and colours of the reference tool's progress box (its sidebar "Report Progress"). */
const ROWS: { status: CheckStatus; dot: string }[] = [
  { status: 'VERIFIED', dot: 'bg-emerald-600 text-white' },
  { status: 'DISCREPANCY', dot: 'bg-red-600 text-white' },
  { status: 'UNABLE_TO_VERIFY', dot: 'bg-amber-700 text-white' },
  { status: 'CLOSED', dot: 'bg-gray-500 text-white' },
  { status: 'PENDING', dot: 'bg-yellow-400 text-slate-900' },
  { status: 'IN_PROGRESS', dot: 'bg-blue-600 text-white' },
]

/** A check is finished once it has an outcome. Pending and In Progress are still work to do (as in the reference tool). */
const CONCLUDED: CheckStatus[] = ['VERIFIED', 'DISCREPANCY', 'UNABLE_TO_VERIFY', 'CLOSED']

/** "3 (60%)", or "0 (0%)" when there are no checks. */
function share(count: number, total: number): string {
  return total === 0 ? '0 (0%)' : `${count} (${Math.round((count / total) * 100)}%)`
}

/** The bar's colour changes as the work advances: amber under half, teal from half, green when everything is finished. */
function barColour(percent: number): string {
  return percent === 100 ? 'bg-emerald-600' : percent >= 50 ? 'bg-cyan-600' : 'bg-amber-500'
}

/**
 * The status of the case's checks: how many there are, how many are in each of the six states, and how far along the
 * work is. Ported from the reference tool's "Report Progress" box (CLAUDE.md section 6.2 / audit item 33). The numbers
 * come from the server after every save (they are not recalculated while typing).
 */
export function ReportProgress({ progress }: { progress: Progress | undefined }) {
  const total = progress?.totalChecks ?? 0
  const counts = progress?.checksByStatus ?? {}
  const done = CONCLUDED.reduce((sum, status) => sum + (counts[status] ?? 0), 0)
  const percent = total > 0 ? Math.round((done / total) * 100) : 0

  return (
    <section aria-label="Report progress" className="flex flex-col gap-2 rounded-lg border border-line bg-brand-50/60 p-3">
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-sm font-semibold text-brand-800">Report progress</h2>
        {percent === 100 && total > 0 && (
          <span className="rounded-full bg-emerald-700 px-2 py-0.5 text-xs font-semibold text-white">✓ COMPLETE</span>
        )}
      </div>
      <div className="flex items-center justify-between border-b border-line pb-1.5 text-sm">
        <span className="text-slate-700">Total checks</span>
        <span className="font-semibold text-brand-800">{total}</span>
      </div>
      <ul className="flex flex-col gap-1 text-sm">
        {ROWS.map(({ status, dot }) => (
          <li key={status} className="flex items-center gap-2">
            <span aria-hidden className={cn('inline-flex h-4 w-4 shrink-0 items-center justify-center rounded-full text-[10px] font-bold', dot)}>
              {STATUS_MARKS[status]}
            </span>
            <span className="flex-1 text-slate-800">{STATUS_LABELS[status]}</span>
            <span className="font-semibold text-slate-900">{share(counts[status] ?? 0, total)}</span>
          </li>
        ))}
      </ul>
      <div className="border-t border-line pt-2">
        <div className="mb-1 flex justify-between text-xs font-medium text-slate-700">
          <span>Checks finished</span>
          <span className="font-semibold text-brand-800">
            {done} of {total} ({percent}%)
          </span>
        </div>
        <div
          role="progressbar"
          aria-label="Checks finished"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={percent}
          className="h-2 overflow-hidden rounded-full bg-slate-200"
        >
          <div className={cn('h-full transition-all', barColour(percent))} style={{ width: `${percent}%` }} />
        </div>
      </div>
    </section>
  )
}
