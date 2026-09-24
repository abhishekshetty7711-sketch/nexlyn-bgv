import { useState } from 'react'
import { Link } from 'react-router-dom'
import { describeError } from '@/api/errors'
import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Spinner } from '@/components/ui/spinner'
import { Can } from '@/features/auth/Can'
import { CASES_PAGE_SIZE, type CaseFilters, EMPTY_CASE_FILTERS, useCases, useClients } from './api'
import { formatDate } from './format'
import { NewCaseDialog } from './NewCaseDialog'
import { LIFECYCLE_LABELS, type Lifecycle } from './types'

const TONES: Record<Lifecycle, 'neutral' | 'green' | 'amber'> = {
  DRAFT: 'neutral',
  IN_REVIEW: 'amber',
  CHANGES_REQUESTED: 'amber',
  APPROVED: 'green',
  FINALIZED: 'green',
}

/** The case list: search, filter by status or client, and open a case. Analysts only ever see their own cases. */
export function CasesPage() {
  const [draft, setDraft] = useState<CaseFilters>(EMPTY_CASE_FILTERS)
  const [applied, setApplied] = useState<CaseFilters>(EMPTY_CASE_FILTERS)
  const [page, setPage] = useState(0)
  const [creating, setCreating] = useState(false)
  const cases = useCases(applied, page)
  const clients = useClients()
  const totalPages = cases.data ? Math.max(1, Math.ceil(cases.data.total / CASES_PAGE_SIZE)) : 1

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-slate-900">Cases</h1>
        <Can permission="CASE_CREATE">
          <Button onClick={() => setCreating(true)}>New case</Button>
        </Can>
      </div>

      <Card>
        <form
          className="grid gap-3 md:grid-cols-4"
          onSubmit={(event) => {
            event.preventDefault()
            setPage(0)
            setApplied(draft)
          }}
        >
          <label className="flex flex-col gap-1 text-xs font-medium text-slate-600 md:col-span-2">
            Search
            <Input
              type="search"
              placeholder="Report ID, candidate name or employee ID"
              value={draft.q}
              onChange={(event) => setDraft({ ...draft, q: event.target.value })}
            />
          </label>
          <label className="flex flex-col gap-1 text-xs font-medium text-slate-600">
            Status
            <Select value={draft.status} onChange={(event) => setDraft({ ...draft, status: event.target.value as Lifecycle | '' })}>
              <option value="">All statuses</option>
              {(Object.keys(LIFECYCLE_LABELS) as Lifecycle[]).map((key) => (
                <option key={key} value={key}>
                  {LIFECYCLE_LABELS[key]}
                </option>
              ))}
            </Select>
          </label>
          <label className="flex flex-col gap-1 text-xs font-medium text-slate-600">
            Client
            <Select value={draft.client} onChange={(event) => setDraft({ ...draft, client: event.target.value })}>
              <option value="">All clients</option>
              {clients.data?.map((client) => (
                <option key={client.id} value={client.id}>
                  {client.name}
                </option>
              ))}
            </Select>
          </label>
          <div className="flex gap-2 md:col-span-4">
            <Button type="submit" size="sm">
              Apply filters
            </Button>
            <Button
              type="button"
              size="sm"
              variant="outline"
              onClick={() => {
                setDraft(EMPTY_CASE_FILTERS)
                setApplied(EMPTY_CASE_FILTERS)
                setPage(0)
              }}
            >
              Clear
            </Button>
          </div>
        </form>
      </Card>

      {cases.isLoading && <Spinner />}
      {cases.isError && <Alert variant="error">{describeError(cases.error)}</Alert>}
      {cases.data && (
        <Card className="overflow-x-auto p-0">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
              <tr>
                <th className="px-3 py-2">Report ID</th>
                <th className="px-3 py-2">Candidate</th>
                <th className="px-3 py-2">Client</th>
                <th className="px-3 py-2">Status</th>
                <th className="px-3 py-2">Assigned</th>
                <th className="px-3 py-2">Issued</th>
                <th className="px-3 py-2">Updated</th>
              </tr>
            </thead>
            <tbody>
              {cases.data.items.length === 0 && (
                <tr>
                  <td className="px-3 py-4 text-slate-500" colSpan={7}>
                    No cases match.
                  </td>
                </tr>
              )}
              {cases.data.items.map((row) => (
                <tr key={row.id} className="border-b border-slate-100 last:border-0">
                  <td className="px-3 py-2 font-medium">
                    <Link className="text-slate-900 underline" to={`/cases/${row.id}`}>
                      {row.reportId}
                    </Link>
                  </td>
                  <td className="px-3 py-2">
                    <div>{row.candidateName ?? <span className="text-slate-400">Not entered yet</span>}</div>
                    {row.employeeId && <div className="text-xs text-slate-500">{row.employeeId}</div>}
                  </td>
                  <td className="px-3 py-2">{row.clientName}</td>
                  <td className="px-3 py-2">
                    <Badge tone={TONES[row.lifecycle]}>{LIFECYCLE_LABELS[row.lifecycle]}</Badge>
                  </td>
                  <td className="px-3 py-2 text-xs text-slate-600">{row.assignments.map((person) => person.fullName).join(', ') || '-'}</td>
                  <td className="whitespace-nowrap px-3 py-2 text-slate-600">{formatDate(row.issueDate)}</td>
                  <td className="whitespace-nowrap px-3 py-2 text-slate-600">{new Date(row.updatedAt).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="flex items-center justify-between border-t border-slate-200 px-3 py-2 text-xs text-slate-500">
            <span>{cases.data.total} cases</span>
            <div className="flex items-center gap-2">
              <Button size="sm" variant="outline" disabled={page === 0} onClick={() => setPage(page - 1)}>
                Previous
              </Button>
              <span>
                Page {page + 1} of {totalPages}
              </span>
              <Button size="sm" variant="outline" disabled={page + 1 >= totalPages} onClick={() => setPage(page + 1)}>
                Next
              </Button>
            </div>
          </div>
        </Card>
      )}

      {creating && <NewCaseDialog onClose={() => setCreating(false)} />}
    </div>
  )
}
