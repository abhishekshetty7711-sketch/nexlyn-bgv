import { Link } from 'react-router-dom'
import { CalendarClock, CheckCircle2, ClipboardCheck, UserRound, type LucideIcon } from 'lucide-react'
import { describeError } from '@/api/errors'
import { useHealthQuery } from '@/api/queries/health'
import { Alert } from '@/components/ui/alert'
import { Card } from '@/components/ui/card'
import { Skeleton, SkeletonRows } from '@/components/ui/skeleton'
import { useAuth } from '@/features/auth/AuthContext'
import { formatDate } from '../cases/format'
import { LifecycleBadge, LIFECYCLE_LOOK } from '../cases/LifecycleBadge'
import { LIFECYCLE_LABELS, type CaseRow, type Lifecycle } from '../cases/types'
import { useDashboard } from './api'

const STAGES: Lifecycle[] = ['DRAFT', 'IN_REVIEW', 'CHANGES_REQUESTED', 'APPROVED', 'FINALIZED']

// The accent line and icon tint of each stage tile (decoration: the number and the label carry the meaning).
const TILE_ACCENT: Record<Lifecycle, { border: string; tint: string }> = {
  DRAFT: { border: 'border-l-slate-400', tint: 'bg-slate-100 text-slate-700' },
  IN_REVIEW: { border: 'border-l-sky-500', tint: 'bg-sky-100 text-sky-800' },
  CHANGES_REQUESTED: { border: 'border-l-amber-500', tint: 'bg-amber-100 text-amber-900' },
  APPROVED: { border: 'border-l-emerald-500', tint: 'bg-emerald-100 text-emerald-800' },
  FINALIZED: { border: 'border-l-brand-800', tint: 'bg-brand-100 text-brand-800' },
}

/** Today as YYYY-MM-DD in the browser's time zone, to tell a due date that has passed. */
function today(): string {
  return new Date().toLocaleDateString('en-CA')
}

function CaseList({
  title,
  icon: Icon,
  rows,
  empty,
  dueDates = false,
}: {
  title: string
  icon: LucideIcon
  rows: CaseRow[]
  empty: string
  dueDates?: boolean
}) {
  const now = today()
  return (
    <Card className="self-start">
      <section aria-label={title} className="flex flex-col gap-2">
        <div className="flex items-center gap-2">
          <Icon className="h-4 w-4 text-brand-700" aria-hidden />
          <h2 className="flex-1 text-sm font-semibold text-slate-900">{title}</h2>
          {rows.length > 0 && <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-700">{rows.length}</span>}
        </div>
        {rows.length === 0 ? (
          <p className="rounded-md border border-dashed border-line px-3 py-4 text-center text-sm text-slate-600">{empty}</p>
        ) : (
          <ul className="flex flex-col divide-y divide-slate-100">
            {rows.map((row) => {
              const overdue = dueDates && row.dueDate !== null && row.dueDate < now
              return (
                <li key={row.id} className="flex flex-wrap items-center gap-x-3 gap-y-1 py-2 text-sm">
                  <Link to={`/cases/${row.id}`} className="font-semibold text-brand-800 underline-offset-2 hover:underline">
                    {row.reportId}
                  </Link>
                  <span className="min-w-0 flex-1 basis-40 truncate text-slate-700">
                    {row.candidateName ?? 'No candidate name yet'} · {row.clientName}
                  </span>
                  {dueDates && row.dueDate && (
                    <span className={overdue ? 'text-xs font-semibold text-red-700' : 'text-xs text-slate-600'}>
                      due {formatDate(row.dueDate, 'NUMERIC')}
                      {overdue ? ' (overdue)' : ''}
                    </span>
                  )}
                  <LifecycleBadge lifecycle={row.lifecycle} />
                </li>
              )
            })}
          </ul>
        )}
      </section>
    </Card>
  )
}

/** The landing page: where every case stands, what is mine, what waits for my review, what is due. */
export function DashboardPage() {
  const { hasPermission, hasAnyPermission } = useAuth()
  const canSeeCases = hasAnyPermission(['CASE_READ_ALL', 'CASE_READ_ASSIGNED'])
  const dashboard = useDashboard(canSeeCases)
  const health = useHealthQuery()
  const status = health.isLoading ? 'Checking...' : (health.data?.status ?? 'DOWN')
  const data = dashboard.data

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-5">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Dashboard</h1>
        <p className="text-sm text-slate-600">Where every case stands, and what needs you.</p>
      </div>

      {!canSeeCases && <Alert variant="info">Your account does not include access to cases. Use the menu for the areas you can open.</Alert>}
      {canSeeCases && dashboard.isLoading && (
        <>
          <div className="grid gap-3 sm:grid-cols-3 lg:grid-cols-5" aria-hidden>
            {STAGES.map((stage) => (
              <Skeleton key={stage} className="h-24 rounded-xl" />
            ))}
          </div>
          <SkeletonRows rows={5} />
        </>
      )}
      {canSeeCases && dashboard.isError && <Alert variant="error">{describeError(dashboard.error)}</Alert>}

      {data && (
        <>
          <section aria-label="Cases by stage" className="grid gap-3 sm:grid-cols-3 lg:grid-cols-5">
            {STAGES.map((stage) => {
              const count = data.counts[stage] ?? 0
              const Icon = LIFECYCLE_LOOK[stage].icon
              const accent = TILE_ACCENT[stage]
              return (
                <Link
                  key={stage}
                  to={`/cases?status=${stage}`}
                  className={`flex items-center gap-3 rounded-xl border border-l-4 border-line bg-white p-4 shadow-sm transition-shadow hover:shadow-md ${accent.border}`}
                  aria-label={`${LIFECYCLE_LABELS[stage]}: ${count} cases`}
                >
                  <span className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-full ${accent.tint}`} aria-hidden>
                    <Icon className="h-5 w-5" />
                  </span>
                  <span>
                    <span className="block text-2xl font-semibold leading-none text-slate-900">{count}</span>
                    <span className="mt-1 block text-xs font-medium text-slate-600">{LIFECYCLE_LABELS[stage]}</span>
                  </span>
                </Link>
              )
            })}
          </section>

          {data.overdue > 0 && (
            <Alert variant="error">
              {data.overdue} case{data.overdue === 1 ? ' is' : 's are'} overdue. See &ldquo;Due soon and overdue&rdquo; below.
            </Alert>
          )}

          <div className="grid items-start gap-4 lg:grid-cols-2">
            {hasPermission('REPORT_APPROVE') && (
              <CaseList title="Waiting for my review" icon={ClipboardCheck} rows={data.awaitingMyReview} empty="Nothing is waiting for your review." />
            )}
            <CaseList title="My cases" icon={UserRound} rows={data.mine} empty="No open cases are assigned to you." />
            <CaseList title="Due soon and overdue" icon={CalendarClock} rows={data.dueSoon} empty="No case is due in the next 3 days." dueDates />
          </div>
        </>
      )}

      <p className="flex items-center justify-end gap-2 text-xs text-slate-600">
        <CheckCircle2 className={`h-3.5 w-3.5 ${status === 'UP' ? 'text-emerald-600' : 'text-red-600'}`} aria-hidden />
        Backend status: {status}
      </p>
    </div>
  )
}
