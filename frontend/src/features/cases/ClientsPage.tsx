import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { describeError } from '@/api/errors'
import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Dialog } from '@/components/ui/dialog'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'
import { Textarea } from '@/components/ui/textarea'
import { Can } from '@/features/auth/Can'
import { useClients, useSaveClient } from './api'
import { type ClientValues, clientSchema } from './schemas'
import type { ClientView } from './types'

/** The companies that order verifications. Anyone who can read cases can look; CLIENT_MANAGE can change. */
export function ClientsPage() {
  const clients = useClients()
  const [editing, setEditing] = useState<ClientView | 'new' | null>(null)

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-slate-900">Clients</h1>
        <Can permission="CLIENT_MANAGE">
          <Button onClick={() => setEditing('new')}>New client</Button>
        </Can>
      </div>
      {clients.isLoading && <Spinner />}
      {clients.isError && <Alert variant="error">{describeError(clients.error)}</Alert>}
      {clients.data && (
        <Card className="overflow-x-auto p-0">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
              <tr>
                <th className="px-3 py-2">Client</th>
                <th className="px-3 py-2">Printed on reports as</th>
                <th className="px-3 py-2">Status</th>
                <th className="px-3 py-2">
                  <span className="sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {clients.data.length === 0 && (
                <tr>
                  <td className="px-3 py-4 text-slate-500" colSpan={4}>
                    No clients yet.
                  </td>
                </tr>
              )}
              {clients.data.map((client) => (
                <tr key={client.id} className="border-b border-slate-100 last:border-0">
                  <td className="px-3 py-2 font-medium text-slate-900">{client.name}</td>
                  <td className="whitespace-pre-line px-3 py-2 text-slate-600">{client.displayName}</td>
                  <td className="px-3 py-2">{client.active ? <Badge tone="green">Active</Badge> : <Badge>Inactive</Badge>}</td>
                  <td className="px-3 py-2 text-right">
                    <Can permission="CLIENT_MANAGE">
                      <Button size="sm" variant="outline" onClick={() => setEditing(client)}>
                        Edit
                      </Button>
                    </Can>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}
      {editing && <ClientDialog client={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
    </div>
  )
}

function ClientDialog({ client, onClose }: { client: ClientView | null; onClose: () => void }) {
  const save = useSaveClient()
  const form = useForm<ClientValues>({
    resolver: zodResolver(clientSchema),
    defaultValues: {
      name: client?.name ?? '',
      displayName: client?.displayName ?? '',
      defaultCheckTypes: client?.defaultCheckTypes.join(', ') ?? '',
      active: client?.active ?? true,
    },
  })
  const { errors } = form.formState

  const submit = form.handleSubmit((values) =>
    save.mutate(
      {
        id: client?.id,
        version: client?.version,
        input: {
          name: values.name.trim(),
          displayName: values.displayName.trim(),
          defaultCheckTypes: values.defaultCheckTypes.split(',').map((type) => type.trim().toUpperCase()).filter(Boolean),
          active: values.active,
        },
      },
      { onSuccess: onClose },
    ),
  )

  return (
    <Dialog title={client ? `Edit ${client.name}` : 'New client'} onClose={onClose}>
      <form className="flex flex-col gap-3" onSubmit={submit} noValidate>
        {save.isError && <Alert variant="error">{describeError(save.error)}</Alert>}
        <Field label="Client name" htmlFor="cl-name" error={errors.name?.message}>
          <Input id="cl-name" aria-invalid={!!errors.name} {...form.register('name')} />
        </Field>
        <Field
          label="Name on reports"
          htmlFor="cl-display"
          hint="Exactly as it should print. Use new lines for line breaks."
          error={errors.displayName?.message}
        >
          <Textarea id="cl-display" rows={3} aria-invalid={!!errors.displayName} {...form.register('displayName')} />
        </Field>
        <Field
          label="Usual checks (optional)"
          htmlFor="cl-checks"
          hint="Comma-separated, for example AADHAAR, PAN, EDUCATION. Used to suggest checks on new cases."
          error={errors.defaultCheckTypes?.message}
        >
          <Input id="cl-checks" {...form.register('defaultCheckTypes')} />
        </Field>
        <label className="flex items-center gap-2 text-sm text-slate-700">
          <input type="checkbox" {...form.register('active')} /> Active (can be chosen for new cases)
        </label>
        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={save.isPending}>
            Save
          </Button>
        </div>
      </form>
    </Dialog>
  )
}
