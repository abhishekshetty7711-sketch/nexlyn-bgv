import { Link } from 'react-router-dom'
import { describeError } from '@/api/errors'
import { useHealthQuery } from '@/api/queries/health'
import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Card } from '@/components/ui/card'
import { Spinner } from '@/components/ui/spinner'
import { useAuth } from '@/features/auth/AuthContext'
import { formatDate } from '../cases/format'
import { LIFECYCLE_LABELS, type CaseRow, type Lifecycle } from '../cases/types'
import { useDashboard } from './api'

const STAGES: Lifecycle[] = ['DRAFT', 'IN_REVIEW', 'CHANGES_REQUESTED', 'APPROVED', 'FINALIZED']

function CaseList({ title, rows, empty, dueDates = false }: { title: string; rows: CaseRow[]; empty: string; dueDates?: boolean }) {
  return (
    <Card>
      <section aria-label={title} className="flex flex-col gap-2">
        <h2 className="text-sm font-semibold text-slate-800">{title}</h2>
        {rows.length === 0 ? (
          <p className="text-sm text-slate-500">{empty}</p>
        ) : (
          <ul className="flex flex-col divide-y divide-slate-100">
            {rows.map((row) => (
              <li key={row.id} className="flex items-center gap-3 py-2 text-sm">
                <Link to={`/cases/${row.id}`} className="font-medium text-slate-900 underline-offset-2 hover:underline">
                  {row.reportId}
                </Link>
                <span className="min-w-0 flex-1 truncate text-slate-600">
                  {row.candidateName ?? 'No candidate name yet'} · {row.clientName}
                </span>
                {dueDates && row.dueDate && <span className="text-xs text-slate-500">due {formatDate(row.dueDate, 'NUMERIC')}</span>}
                <Badge tone={row.lifecycle === 'APPROVED' || row.lifecycle === 'FINALIZED' ? 'green' : row.lifecycle === 'DRAFT' ? 'neutral' : 'amber'}>
                  {LIFECYCLE_LABELS[row.lifecycle]}
                </Badge>
              </li>
            ))}
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
    <div className="flex flex-col gap-4">
      <h1 className="text-2xl font-semibold text-slate-900">Dashboard</h1>

      {!canSeeCases && <Alert variant="info">Your account does not include access to cases. Use the menu for the areas you can open.</Alert>}
      {canSeeCases && dashboard.isLoading && <Spinner />}
      {canSeeCases && dashboard.isError && <Alert variant="error">{describeError(dashboard.error)}</Alert>}

      {data && (
        <>
          <section aria-label="Cases by stage" className="grid gap-3 sm:grid-cols-3 lg:grid-cols-5">
            {STAGES.map((stage) => (
              <Link key={stage} to={`/cases?status=${stage}`} className="rounded-lg border border-slate-200 bg-white p-4 hover:bg-slate-50" aria-label={`${LIFECYCLE_LABELS[stage]}: ${data.counts[stage] ?? 0} cases`}>
                <p className="text-2xl font-semibold text-slate-900">{data.counts[stage] ?? 0}</p>
                <p className="text-xs text-slate-500">{LIFECYCLE_LABELS[stage]}</p>
              </Link>
            ))}
          </section>

          {data.overdue > 0 && (
            <Alert variant="error">
              {data.overdue} case{data.overdue === 1 ? ' is' : 's are'} overdue. See &ldquo;Due soon and overdue&rdquo; below.
            </Alert>
          )}

          <div className="grid gap-4 lg:grid-cols-2">
            {hasPermission('REPORT_APPROVE') && (
              <CaseList title="Waiting for my review" rows={data.awaitingMyReview} empty="Nothing is waiting for your review." />
            )}
            <CaseList title="My cases" rows={data.mine} empty="No open cases are assigned to you." />
            <CaseList title="Due soon and overdue" rows={data.dueSoon} empty="No case is due in the next 3 days." dueDates />
          </div>
        </>
      )}

      <div className="flex items-center gap-3 rounded-lg border border-slate-200 bg-white p-3 text-sm text-slate-500">
        <span className={`h-2.5 w-2.5 rounded-full ${status === 'UP' ? 'bg-emerald-500' : 'bg-red-500'}`} aria-hidden />
        Backend status: {status}
      </div>
    </div>
  )
}
