import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { describeError } from '@/api/errors'
import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Dialog } from '@/components/ui/dialog'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'
import { useAuth } from '@/features/auth/AuthContext'
import { Can } from '@/features/auth/Can'
import { type RoleView, useRoles } from '@/features/roles/api'
import { usePageTitle } from '@/lib/usePageTitle'
import {
  type AdminAction,
  type AdminView,
  type IssuedInvitation,
  PAGE_SIZE,
  useAdminAction,
  useAdmins,
  useInviteAdmin,
  usePendingInvitations,
  useRevokeInvitation,
  useUpdateAdmin,
} from './api'

const ACTION_TEXT: Record<AdminAction, { title: string; body: string; button: string }> = {
  disable: {
    title: 'Disable this admin?',
    body: 'They are signed out immediately and cannot sign in until you enable them again.',
    button: 'Disable',
  },
  enable: { title: 'Enable this admin?', body: 'They will be able to sign in again.', button: 'Enable' },
  unlock: {
    title: 'Unlock this account?',
    body: 'Clears the temporary lock after failed sign-in attempts.',
    button: 'Unlock',
  },
  'revoke-sessions': {
    title: 'End all sessions of this admin?',
    body: 'All their sessions end now. They can sign in again.',
    button: 'End sessions',
  },
}

function isLocked(admin: AdminView): boolean {
  return admin.lockedUntil !== null && new Date(admin.lockedUntil).getTime() > Date.now()
}

function formatWhen(value: string | null): string {
  return value ? new Date(value).toLocaleString() : 'Never'
}

export function AdminsPage() {
  usePageTitle('Admins')
  const { state } = useAuth()
  const [page, setPage] = useState(0)
  const admins = useAdmins(page)
  const roles = useRoles()
  const invitations = usePendingInvitations()
  const revokeInvitation = useRevokeInvitation()
  const [inviting, setInviting] = useState(false)
  const [editing, setEditing] = useState<AdminView | null>(null)
  const [confirming, setConfirming] = useState<{ admin: AdminView; action: AdminAction } | null>(null)
  const myId = state.status === 'authenticated' ? state.me.id : null

  const totalPages = admins.data ? Math.max(1, Math.ceil(admins.data.total / PAGE_SIZE)) : 1

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-slate-900">Admins</h1>
        <Can permission="USER_MANAGE">
          <Button onClick={() => setInviting(true)}>Invite admin</Button>
        </Can>
      </div>

      {admins.isLoading && <Spinner />}
      {admins.isError && <Alert variant="error">{describeError(admins.error)}</Alert>}
      {admins.data && (
        <Card className="overflow-x-auto p-0">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
              <tr>
                <th className="px-3 py-2">Admin</th>
                <th className="px-3 py-2">Roles</th>
                <th className="px-3 py-2">Status</th>
                <th className="px-3 py-2">2FA</th>
                <th className="px-3 py-2">Last sign-in</th>
                <th className="px-3 py-2">
                  <span className="sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {admins.data.items.map((admin) => (
                <tr key={admin.id} className="border-b border-slate-100 last:border-0">
                  <td className="px-3 py-2">
                    <div className="font-medium text-slate-900">{admin.fullName}</div>
                    <div className="text-xs text-slate-500">{admin.email}</div>
                  </td>
                  <td className="px-3 py-2">
                    <div className="flex flex-wrap gap-1">
                      {admin.roles.map((role) => (
                        <Badge key={role}>{role}</Badge>
                      ))}
                    </div>
                  </td>
                  <td className="px-3 py-2">
                    {admin.status === 'DISABLED' ? (
                      <Badge tone="red">Disabled</Badge>
                    ) : isLocked(admin) ? (
                      <Badge tone="amber">Locked</Badge>
                    ) : (
                      <Badge tone="green">Active</Badge>
                    )}
                  </td>
                  <td className="px-3 py-2">{admin.mfaEnabled ? 'On' : 'Not set up'}</td>
                  <td className="px-3 py-2 text-slate-600">{formatWhen(admin.lastLoginAt)}</td>
                  <td className="px-3 py-2">
                    <Can permission="USER_MANAGE">
                      <div className="flex flex-wrap justify-end gap-1">
                        <Button size="sm" variant="outline" onClick={() => setEditing(admin)}>
                          Edit
                        </Button>
                        {admin.status === 'DISABLED' ? (
                          <Button size="sm" variant="outline" onClick={() => setConfirming({ admin, action: 'enable' })}>
                            Enable
                          </Button>
                        ) : (
                          admin.id !== myId && (
                            <Button size="sm" variant="outline" onClick={() => setConfirming({ admin, action: 'disable' })}>
                              Disable
                            </Button>
                          )
                        )}
                        {isLocked(admin) && (
                          <Button size="sm" variant="outline" onClick={() => setConfirming({ admin, action: 'unlock' })}>
                            Unlock
                          </Button>
                        )}
                        <Button size="sm" variant="ghost" onClick={() => setConfirming({ admin, action: 'revoke-sessions' })}>
                          End sessions
                        </Button>
                      </div>
                    </Can>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="flex items-center justify-between border-t border-slate-200 px-3 py-2 text-xs text-slate-500">
            <span>{admins.data.total} admins</span>
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

      <Card>
        <h2 className="mb-2 text-sm font-semibold text-slate-900">Pending invitations</h2>
        {invitations.data && invitations.data.length === 0 && <p className="text-sm text-slate-500">None.</p>}
        <ul className="flex flex-col gap-2">
          {invitations.data?.map((invitation) => (
            <li key={invitation.id} className="flex items-center justify-between gap-2 text-sm">
              <span>
                {invitation.email} <span className="text-slate-500">({invitation.roles.join(', ')})</span>
                <span className="ml-2 text-xs text-slate-500">expires {new Date(invitation.expiresAt).toLocaleString()}</span>
              </span>
              <Button size="sm" variant="outline" disabled={revokeInvitation.isPending} onClick={() => revokeInvitation.mutate(invitation.id)}>
                Revoke
              </Button>
            </li>
          ))}
        </ul>
        {revokeInvitation.isError && <Alert variant="error">{describeError(revokeInvitation.error)}</Alert>}
      </Card>

      {inviting && <InviteDialog roles={roles.data ?? []} onClose={() => setInviting(false)} />}
      {editing && <EditAdminDialog admin={editing} roles={roles.data ?? []} onClose={() => setEditing(null)} />}
      {confirming && <ConfirmActionDialog admin={confirming.admin} action={confirming.action} onClose={() => setConfirming(null)} />}
    </div>
  )
}

function RolePicker({ roles, selected, onChange }: { roles: RoleView[]; selected: string[]; onChange: (ids: string[]) => void }) {
  return (
    <fieldset className="flex flex-col gap-1">
      <legend className="mb-1 text-sm font-medium text-slate-700">Roles</legend>
      {roles.map((role) => (
        <label key={role.id} className="flex items-start gap-2 text-sm text-slate-700">
          <input
            type="checkbox"
            className="mt-1"
            checked={selected.includes(role.id)}
            onChange={(event) => onChange(event.target.checked ? [...selected, role.id] : selected.filter((id) => id !== role.id))}
          />
          <span>
            {role.name} <span className="text-xs text-slate-500">{role.description}</span>
          </span>
        </label>
      ))}
    </fieldset>
  )
}

const inviteSchema = z.object({
  email: z.string().trim().min(1, 'Enter an email address').pipe(z.email('Enter a valid email address')),
})

function InviteDialog({ roles, onClose }: { roles: RoleView[]; onClose: () => void }) {
  const invite = useInviteAdmin()
  const [roleIds, setRoleIds] = useState<string[]>([])
  const [issued, setIssued] = useState<IssuedInvitation | null>(null)
  const [copied, setCopied] = useState(false)
  const form = useForm<{ email: string }>({ resolver: zodResolver(inviteSchema), defaultValues: { email: '' } })

  if (issued) {
    const link = `${window.location.origin}/accept-invite?token=${encodeURIComponent(issued.inviteToken)}`
    return (
      <Dialog title="Invitation created" onClose={onClose}>
        <div className="flex flex-col gap-3">
          <Alert variant="warning">
            Send this link to {issued.email}. It is shown only now, works once, and expires on{' '}
            {new Date(issued.expiresAt).toLocaleString()}.
          </Alert>
          <Input readOnly value={link} aria-label="Invitation link" onFocus={(event) => event.target.select()} />
          <div className="flex justify-end gap-2">
            <Button
              variant="outline"
              onClick={() => {
                void navigator.clipboard?.writeText(link).then(() => setCopied(true))
              }}
            >
              {copied ? 'Copied' : 'Copy link'}
            </Button>
            <Button onClick={onClose}>Done</Button>
          </div>
        </div>
      </Dialog>
    )
  }

  return (
    <Dialog title="Invite an admin" onClose={onClose}>
      <form
        className="flex flex-col gap-3"
        noValidate
        onSubmit={form.handleSubmit((values) => invite.mutate({ email: values.email, roleIds }, { onSuccess: setIssued }))}
      >
        {invite.isError && <Alert variant="error">{describeError(invite.error)}</Alert>}
        <Field label="Email" htmlFor="invite-email" required error={form.formState.errors.email?.message}>
          <Input id="invite-email" type="email" aria-invalid={!!form.formState.errors.email} {...form.register('email')} />
        </Field>
        <RolePicker roles={roles} selected={roleIds} onChange={setRoleIds} />
        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={invite.isPending || roleIds.length === 0}>
            Create invitation
          </Button>
        </div>
      </form>
    </Dialog>
  )
}

function EditAdminDialog({ admin, roles, onClose }: { admin: AdminView; roles: RoleView[]; onClose: () => void }) {
  const update = useUpdateAdmin()
  const [fullName, setFullName] = useState(admin.fullName)
  const [roleIds, setRoleIds] = useState<string[]>(() =>
    roles.filter((role) => admin.roles.includes(role.code)).map((role) => role.id),
  )

  return (
    <Dialog title={`Edit ${admin.email}`} onClose={onClose}>
      <form
        className="flex flex-col gap-3"
        onSubmit={(event) => {
          event.preventDefault()
          update.mutate({ id: admin.id, fullName, roleIds }, { onSuccess: onClose })
        }}
      >
        {update.isError && <Alert variant="error">{describeError(update.error)}</Alert>}
        <Alert variant="info">Changing roles signs this admin out everywhere so the new access applies at once.</Alert>
        <Field label="Full name" htmlFor="edit-name">
          <Input id="edit-name" value={fullName} onChange={(event) => setFullName(event.target.value)} />
        </Field>
        <RolePicker roles={roles} selected={roleIds} onChange={setRoleIds} />
        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={update.isPending || roleIds.length === 0 || fullName.trim() === ''}>
            Save
          </Button>
        </div>
      </form>
    </Dialog>
  )
}

function ConfirmActionDialog({ admin, action, onClose }: { admin: AdminView; action: AdminAction; onClose: () => void }) {
  const run = useAdminAction()
  const text = ACTION_TEXT[action]
  return (
    <Dialog title={text.title} onClose={onClose}>
      <div className="flex flex-col gap-3">
        <p className="text-sm text-slate-600">
          <strong>{admin.fullName}</strong> ({admin.email}). {text.body}
        </p>
        {run.isError && <Alert variant="error">{describeError(run.error)}</Alert>}
        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button disabled={run.isPending} onClick={() => run.mutate({ id: admin.id, action }, { onSuccess: onClose })}>
            {text.button}
          </Button>
        </div>
      </div>
    </Dialog>
  )
}
