import { useState } from 'react'
import { describeError } from '@/api/errors'
import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Select } from '@/components/ui/select'
import { Can } from '@/features/auth/Can'
import { useAssign, useAssignableAdmins, useUnassign } from '../api'
import type { CaseRole, CaseView } from '../types'

/** Who prepares and who reviews this case. Assigning needs CASE_ASSIGN; everyone on the case sees the list. */
export function AssignmentsPanel({ caseView }: { caseView: CaseView }) {
  const admins = useAssignableAdmins(true)
  const assign = useAssign(caseView.id)
  const unassign = useUnassign(caseView.id)
  const [adminId, setAdminId] = useState('')
  const [role, setRole] = useState<CaseRole>('REVIEWER')
  const error = assign.error ?? unassign.error
  const locked = caseView.lifecycle === 'FINALIZED'

  return (
    <section aria-label="Assignments" className="flex flex-col gap-2 border-t border-slate-200 pt-3">
      <h2 className="text-xs font-semibold uppercase text-slate-500">Assigned to</h2>
      <ul className="flex flex-col gap-1">
        {caseView.assignments.map((person) => (
          <li key={`${person.adminId}-${person.role}`} className="flex items-center justify-between gap-2 text-sm">
            <span className="min-w-0 truncate" title={person.email ?? undefined}>
              {person.fullName}
            </span>
            <span className="flex items-center gap-1">
              <Badge tone={person.role === 'PREPARER' ? 'neutral' : 'green'}>{person.role === 'PREPARER' ? 'Preparer' : 'Reviewer'}</Badge>
              <Can permission="CASE_ASSIGN">
                {!locked && (
                  <button
                    type="button"
                    className="text-xs text-slate-500 underline"
                    aria-label={`Remove ${person.fullName} as ${person.role.toLowerCase()}`}
                    disabled={unassign.isPending}
                    onClick={() => unassign.mutate({ adminId: person.adminId, role: person.role })}
                  >
                    Remove
                  </button>
                )}
              </Can>
            </span>
          </li>
        ))}
        {caseView.assignments.length === 0 && <li className="text-sm text-slate-500">Nobody yet.</li>}
      </ul>
      <Can permission="CASE_ASSIGN">
        {!locked && (
          <form
            className="flex flex-col gap-2"
            onSubmit={(event) => {
              event.preventDefault()
              if (adminId) {
                assign.mutate({ adminId, role }, { onSuccess: () => setAdminId('') })
              }
            }}
          >
            <Select aria-label="Admin to assign" value={adminId} onChange={(event) => setAdminId(event.target.value)}>
              <option value="">Choose an admin...</option>
              {admins.data?.map((admin) => (
                <option key={admin.id} value={admin.id}>
                  {admin.fullName}
                </option>
              ))}
            </Select>
            <div className="flex gap-2">
              <Select aria-label="Role on this case" value={role} onChange={(event) => setRole(event.target.value as CaseRole)}>
                <option value="REVIEWER">Reviewer</option>
                <option value="PREPARER">Preparer</option>
              </Select>
              <Button type="submit" size="sm" variant="outline" disabled={!adminId || assign.isPending}>
                Assign
              </Button>
            </div>
          </form>
        )}
      </Can>
      {error && <Alert variant="error">{describeError(error)}</Alert>}
    </section>
  )
}
