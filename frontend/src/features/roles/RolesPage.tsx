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
import { Can } from '@/features/auth/Can'
import {
  type PermissionView,
  type RoleView,
  useCreateRole,
  useDeleteRole,
  usePermissions,
  useRoles,
  useUpdateRole,
} from './api'

export function RolesPage() {
  const roles = useRoles()
  const permissions = usePermissions(true)
  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState<RoleView | null>(null)
  const [deleting, setDeleting] = useState<RoleView | null>(null)

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-slate-900">Roles</h1>
        <Can permission="ROLE_MANAGE">
          <Button onClick={() => setCreating(true)}>New role</Button>
        </Can>
      </div>
      <p className="text-sm text-slate-600">
        The built-in roles cannot change what they allow; create a custom role for anything else. You can only give a role
        permissions you hold yourself.
      </p>
      {roles.isLoading && <Spinner />}
      {roles.isError && <Alert variant="error">{describeError(roles.error)}</Alert>}
      <div className="grid gap-3 md:grid-cols-2">
        {roles.data?.map((role) => (
          <Card key={role.id} className="flex flex-col gap-2">
            <div className="flex items-center gap-2">
              <h2 className="font-semibold text-slate-900">{role.name}</h2>
              <Badge tone={role.systemRole ? 'neutral' : 'green'}>{role.systemRole ? 'Built-in' : 'Custom'}</Badge>
              <span className="ml-auto text-xs text-slate-500">
                {role.memberCount} {role.memberCount === 1 ? 'admin' : 'admins'}
              </span>
            </div>
            <p className="text-xs text-slate-500">
              <code>{role.code}</code>
              {role.description ? ` - ${role.description}` : ''}
            </p>
            <div className="flex flex-wrap gap-1">
              {role.permissions.map((permission) => (
                <Badge key={permission}>{permission}</Badge>
              ))}
            </div>
            <Can permission="ROLE_MANAGE">
              <div className="mt-1 flex gap-2">
                <Button size="sm" variant="outline" onClick={() => setEditing(role)}>
                  Edit
                </Button>
                {!role.systemRole && role.memberCount === 0 && (
                  <Button size="sm" variant="outline" onClick={() => setDeleting(role)}>
                    Delete
                  </Button>
                )}
              </div>
            </Can>
          </Card>
        ))}
      </div>

      {creating && <CreateRoleDialog permissions={permissions.data ?? []} onClose={() => setCreating(false)} />}
      {editing && <EditRoleDialog role={editing} permissions={permissions.data ?? []} onClose={() => setEditing(null)} />}
      {deleting && <DeleteRoleDialog role={deleting} onClose={() => setDeleting(null)} />}
    </div>
  )
}

function PermissionPicker({
  permissions,
  selected,
  disabled,
  onChange,
}: {
  permissions: PermissionView[]
  selected: string[]
  disabled?: boolean
  onChange: (codes: string[]) => void
}) {
  return (
    <fieldset className="flex flex-col gap-1" disabled={disabled}>
      <legend className="mb-1 text-sm font-medium text-slate-700">Permissions</legend>
      {permissions.map((permission) => (
        <label key={permission.code} className="flex items-start gap-2 text-sm text-slate-700">
          <input
            type="checkbox"
            className="mt-1"
            checked={selected.includes(permission.code)}
            onChange={(event) =>
              onChange(event.target.checked ? [...selected, permission.code] : selected.filter((code) => code !== permission.code))
            }
          />
          <span>
            <code className="text-xs">{permission.code}</code> <span className="text-xs text-slate-500">{permission.description}</span>
          </span>
        </label>
      ))}
    </fieldset>
  )
}

const createSchema = z.object({
  code: z
    .string()
    .trim()
    .regex(/^[A-Z][A-Z0-9_]{2,49}$/, 'Use 3-50 capital letters, digits or underscores, starting with a letter'),
  name: z.string().trim().min(1, 'Enter a name').max(100, 'That name is too long'),
  description: z.string().trim().max(500, 'That description is too long'),
})
type CreateValues = z.infer<typeof createSchema>

function CreateRoleDialog({ permissions, onClose }: { permissions: PermissionView[]; onClose: () => void }) {
  const create = useCreateRole()
  const [selected, setSelected] = useState<string[]>([])
  const form = useForm<CreateValues>({ resolver: zodResolver(createSchema), defaultValues: { code: '', name: '', description: '' } })

  return (
    <Dialog title="New custom role" onClose={onClose}>
      <form
        className="flex flex-col gap-3"
        noValidate
        onSubmit={form.handleSubmit((values) => create.mutate({ ...values, permissions: selected }, { onSuccess: onClose }))}
      >
        {create.isError && <Alert variant="error">{describeError(create.error)}</Alert>}
        <Field label="Code" htmlFor="role-code" hint="Capital letters, digits, underscores. Cannot be changed later." error={form.formState.errors.code?.message}>
          <Input id="role-code" aria-invalid={!!form.formState.errors.code} {...form.register('code')} />
        </Field>
        <Field label="Name" htmlFor="role-name" error={form.formState.errors.name?.message}>
          <Input id="role-name" aria-invalid={!!form.formState.errors.name} {...form.register('name')} />
        </Field>
        <Field label="Description" htmlFor="role-description" error={form.formState.errors.description?.message}>
          <Input id="role-description" {...form.register('description')} />
        </Field>
        <PermissionPicker permissions={permissions} selected={selected} onChange={setSelected} />
        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={create.isPending}>
            Create role
          </Button>
        </div>
      </form>
    </Dialog>
  )
}

function EditRoleDialog({ role, permissions, onClose }: { role: RoleView; permissions: PermissionView[]; onClose: () => void }) {
  const update = useUpdateRole()
  const [name, setName] = useState(role.name)
  const [description, setDescription] = useState(role.description ?? '')
  const [selected, setSelected] = useState<string[]>(role.permissions)

  return (
    <Dialog title={`Edit ${role.code}`} onClose={onClose}>
      <form
        className="flex flex-col gap-3"
        onSubmit={(event) => {
          event.preventDefault()
          update.mutate(
            { id: role.id, input: { name, description, ...(role.systemRole ? {} : { permissions: selected }) } },
            { onSuccess: onClose },
          )
        }}
      >
        {update.isError && <Alert variant="error">{describeError(update.error)}</Alert>}
        {role.systemRole ? (
          <Alert variant="info">Built-in role: only the name and description can change.</Alert>
        ) : (
          <Alert variant="info">Changing permissions signs out everyone who holds this role.</Alert>
        )}
        <Field label="Name" htmlFor="edit-role-name">
          <Input id="edit-role-name" value={name} onChange={(event) => setName(event.target.value)} />
        </Field>
        <Field label="Description" htmlFor="edit-role-description">
          <Input id="edit-role-description" value={description} onChange={(event) => setDescription(event.target.value)} />
        </Field>
        <PermissionPicker permissions={permissions} selected={selected} disabled={role.systemRole} onChange={setSelected} />
        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={update.isPending || name.trim() === ''}>
            Save
          </Button>
        </div>
      </form>
    </Dialog>
  )
}

function DeleteRoleDialog({ role, onClose }: { role: RoleView; onClose: () => void }) {
  const remove = useDeleteRole()
  return (
    <Dialog title="Delete this role?" onClose={onClose}>
      <div className="flex flex-col gap-3">
        <p className="text-sm text-slate-600">
          <strong>{role.name}</strong> ({role.code}) will be removed. This cannot be undone.
        </p>
        {remove.isError && <Alert variant="error">{describeError(remove.error)}</Alert>}
        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button disabled={remove.isPending} onClick={() => remove.mutate(role.id, { onSuccess: onClose })}>
            Delete
          </Button>
        </div>
      </div>
    </Dialog>
  )
}
