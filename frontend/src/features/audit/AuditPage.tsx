import { useState } from 'react'
import { describeError } from '@/api/errors'
import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'
import { AUDIT_PAGE_SIZE, type AuditFilters, EMPTY_FILTERS, useAuditLog } from './api'

const FILTER_FIELDS: { name: keyof AuditFilters; label: string; type: string; placeholder?: string }[] = [
  { name: 'actor', label: 'Actor (email or id)', type: 'text' },
  { name: 'action', label: 'Action', type: 'text', placeholder: 'e.g. ADMIN_DISABLED' },
  { name: 'entity', label: 'Entity', type: 'text', placeholder: 'ADMIN, ROLE, INVITATION' },
  { name: 'from', label: 'From', type: 'datetime-local' },
  { name: 'to', label: 'Until', type: 'datetime-local' },
]

/** Read-only view of the append-only audit log. Nothing here can change or delete an entry. */
export function AuditPage() {
  const [draft, setDraft] = useState<AuditFilters>(EMPTY_FILTERS)
  const [applied, setApplied] = useState<AuditFilters>(EMPTY_FILTERS)
  const [page, setPage] = useState(0)
  const log = useAuditLog(applied, page)
  const totalPages = log.data ? Math.max(1, Math.ceil(log.data.total / AUDIT_PAGE_SIZE)) : 1

  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-2xl font-semibold text-slate-900">Audit log</h1>
      <Card>
        <form
          className="grid gap-3 md:grid-cols-3 lg:grid-cols-5"
          onSubmit={(event) => {
            event.preventDefault()
            setPage(0)
            setApplied(draft)
          }}
        >
          {FILTER_FIELDS.map((field) => (
            <label key={field.name} className="flex flex-col gap-1 text-xs font-medium text-slate-600">
              {field.label}
              <Input
                type={field.type}
                placeholder={field.placeholder}
                value={draft[field.name]}
                onChange={(event) => setDraft({ ...draft, [field.name]: event.target.value })}
              />
            </label>
          ))}
          <div className="flex items-end gap-2 md:col-span-3 lg:col-span-5">
            <Button type="submit" size="sm">
              Apply filters
            </Button>
            <Button
              type="button"
              size="sm"
              variant="outline"
              onClick={() => {
                setDraft(EMPTY_FILTERS)
                setApplied(EMPTY_FILTERS)
                setPage(0)
              }}
            >
              Clear
            </Button>
          </div>
        </form>
      </Card>

      {log.isLoading && <Spinner />}
      {log.isError && <Alert variant="error">{describeError(log.error)}</Alert>}
      {log.data && (
        <Card className="overflow-x-auto p-0">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
              <tr>
                <th className="px-3 py-2">When</th>
                <th className="px-3 py-2">Who</th>
                <th className="px-3 py-2">Action</th>
                <th className="px-3 py-2">Entity</th>
                <th className="px-3 py-2">Details</th>
              </tr>
            </thead>
            <tbody>
              {log.data.items.length === 0 && (
                <tr>
                  <td className="px-3 py-4 text-slate-500" colSpan={5}>
                    No entries match.
                  </td>
                </tr>
              )}
              {log.data.items.map((entry) => (
                <tr key={entry.id} className="border-b border-slate-100 align-top last:border-0">
                  <td className="whitespace-nowrap px-3 py-2 text-slate-600">{new Date(entry.at).toLocaleString()}</td>
                  <td className="px-3 py-2">
                    <div>{entry.actorEmail ?? 'System'}</div>
                    {entry.ip && <div className="text-xs text-slate-500">{entry.ip}</div>}
                  </td>
                  <td className="px-3 py-2 font-mono text-xs">{entry.action}</td>
                  <td className="px-3 py-2 text-xs text-slate-600">
                    {entry.entityType ?? '-'}
                    {entry.entityId && <div className="break-all text-slate-500">{entry.entityId}</div>}
                  </td>
                  <td className="px-3 py-2">
                    {entry.before || entry.after ? (
                      <details>
                        <summary className="cursor-pointer text-xs text-slate-600">Before / after</summary>
                        <pre className="mt-1 max-w-md overflow-x-auto rounded bg-slate-50 p-2 text-xs">
                          {JSON.stringify({ before: entry.before, after: entry.after }, null, 2)}
                        </pre>
                      </details>
                    ) : (
                      <span className="text-xs text-slate-500">-</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="flex items-center justify-between border-t border-slate-200 px-3 py-2 text-xs text-slate-500">
            <span>{log.data.total} entries, newest first</span>
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
    </div>
  )
}
