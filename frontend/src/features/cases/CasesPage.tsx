import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Plus, Search, SearchX } from 'lucide-react'
import { describeError } from '@/api/errors'
import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { SkeletonRows } from '@/components/ui/skeleton'
import { Can } from '@/features/auth/Can'
import { CASES_PAGE_SIZE, type CaseFilters, EMPTY_CASE_FILTERS, useCases, useClients } from './api'
import { formatDate } from './format'
import { LifecycleBadge } from './LifecycleBadge'
import { NewCaseDialog } from './NewCaseDialog'
import { LIFECYCLE_LABELS, type Lifecycle } from './types'

/** The case list: search, filter by status or client, and open a case. Analysts only ever see their own cases. */
export function CasesPage() {
  // A link such as /cases?status=IN_REVIEW (from the dashboard) opens the list already filtered.
  const [search] = useSearchParams()
  const linked = search.get('status')
  const startFilters: CaseFilters = { ...EMPTY_CASE_FILTERS, status: linked && linked in LIFECYCLE_LABELS ? (linked as Lifecycle) : '' }
  const [draft, setDraft] = useState<CaseFilters>(startFilters)
  const [applied, setApplied] = useState<CaseFilters>(startFilters)
  const [page, setPage] = useState(0)
  const [creating, setCreating] = useState(false)
  const cases = useCases(applied, page)
  const clients = useClients()
  const totalPages = cases.data ? Math.max(1, Math.ceil(cases.data.total / CASES_PAGE_SIZE)) : 1

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Cases</h1>
          <p className="text-sm text-slate-600">Search, filter and open a case.</p>
        </div>
        <Can permission="CASE_CREATE">
          <Button onClick={() => setCreating(true)}>
            <Plus className="h-4 w-4" aria-hidden />
            New case
          </Button>
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
          <label className="flex flex-col gap-1 text-xs font-medium text-slate-700 md:col-span-2">
            Search
            <span className="relative">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" aria-hidden />
              <Input
                type="search"
                className="pl-9"
                placeholder="Report ID, candidate name or employee ID"
                value={draft.q}
                onChange={(event) => setDraft({ ...draft, q: event.target.value })}
              />
            </span>
          </label>
          <label className="flex flex-col gap-1 text-xs font-medium text-slate-700">
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
          <label className="flex flex-col gap-1 text-xs font-medium text-slate-700">
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

      {cases.isLoading && (
        <Card>
          <SkeletonRows rows={6} />
        </Card>
      )}
      {cases.isError && <Alert variant="error">{describeError(cases.error)}</Alert>}
      {cases.data && (
        <Card className="overflow-x-auto p-0">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-line bg-slate-50 text-xs font-semibold uppercase tracking-wide text-slate-600">
              <tr>
                <th scope="col" className="px-4 py-3">Report ID</th>
                <th scope="col" className="px-4 py-3">Candidate</th>
                <th scope="col" className="px-4 py-3">Client</th>
                <th scope="col" className="px-4 py-3">Status</th>
                <th scope="col" className="hidden px-4 py-3 md:table-cell">Assigned</th>
                <th scope="col" className="hidden px-4 py-3 md:table-cell">Issued</th>
                <th scope="col" className="hidden px-4 py-3 lg:table-cell">Updated</th>
              </tr>
            </thead>
            <tbody>
              {cases.data.items.length === 0 && (
                <tr>
                  <td className="px-4 py-10 text-center" colSpan={7}>
                    <SearchX className="mx-auto mb-2 h-6 w-6 text-slate-500" aria-hidden />
                    <p className="font-medium text-slate-800">No cases match.</p>
                    <p className="text-xs text-slate-600">Try a different search, or clear the filters.</p>
                  </td>
                </tr>
              )}
              {cases.data.items.map((row) => (
                <tr key={row.id} className="border-b border-slate-100 transition-colors last:border-0 hover:bg-brand-50">
                  <td className="px-4 py-3 font-semibold">
                    <Link className="text-brand-800 underline-offset-2 hover:underline" to={`/cases/${row.id}`}>
                      {row.reportId}
                    </Link>
                  </td>
                  <td className="px-4 py-3">
                    <div className="text-slate-900">{row.candidateName ?? <span className="text-slate-600 italic">Not entered yet</span>}</div>
                    {row.employeeId && <div className="text-xs text-slate-600">{row.employeeId}</div>}
                  </td>
                  <td className="px-4 py-3 text-slate-800">{row.clientName}</td>
                  <td className="px-4 py-3">
                    <LifecycleBadge lifecycle={row.lifecycle} />
                  </td>
                  <td className="hidden px-4 py-3 text-xs text-slate-700 md:table-cell">{row.assignments.map((person) => person.fullName).join(', ') || '-'}</td>
                  <td className="hidden whitespace-nowrap px-4 py-3 text-slate-700 md:table-cell">{formatDate(row.issueDate)}</td>
                  <td className="hidden whitespace-nowrap px-4 py-3 text-slate-700 lg:table-cell">{new Date(row.updatedAt).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="flex items-center justify-between border-t border-line px-4 py-3 text-xs text-slate-700">
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
