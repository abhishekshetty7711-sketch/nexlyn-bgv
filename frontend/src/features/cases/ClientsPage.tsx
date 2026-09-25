import { useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
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
import { usePageTitle } from '@/lib/usePageTitle'
import { useClients, useSaveClient } from './api'
import { useCheckTypes } from './checks/api'
import { type ClientValues, clientSchema } from './schemas'
import type { ClientView } from './types'

/** The companies that order verifications. Anyone who can read cases can look; CLIENT_MANAGE can change. */
export function ClientsPage() {
  usePageTitle('Clients')
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
  const types = useCheckTypes()
  const form = useForm<ClientValues>({
    resolver: zodResolver(clientSchema),
    defaultValues: {
      name: client?.name ?? '',
      displayName: client?.displayName ?? '',
      defaultCheckTypes: client?.defaultCheckTypes ?? [],
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
          defaultCheckTypes: values.defaultCheckTypes,
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
        <Field label="Client name" htmlFor="cl-name" required error={errors.name?.message}>
          <Input id="cl-name" aria-invalid={!!errors.name} {...form.register('name')} />
        </Field>
        <Field
          label="Name on reports"
          htmlFor="cl-display"
          required
          hint="Exactly as it should print. Use new lines for line breaks."
          error={errors.displayName?.message}
        >
          <Textarea id="cl-display" rows={3} aria-invalid={!!errors.displayName} {...form.register('displayName')} />
        </Field>
        <Controller
          control={form.control}
          name="defaultCheckTypes"
          render={({ field }) => {
            const known = new Set((types.data ?? []).map((type) => type.code))
            const options = [...(types.data ?? []).map((type) => ({ code: type.code, name: type.displayName })), ...field.value.filter((code) => !known.has(code)).map((code) => ({ code, name: code }))]
            return (
              <fieldset className="flex flex-col gap-1">
                <legend className="text-sm font-medium text-slate-700">Usual checks (optional)</legend>
                <p className="text-xs text-slate-500">These are offered first when adding a check to this client&apos;s cases.</p>
                <div className="grid max-h-48 gap-1 overflow-y-auto rounded-md border border-slate-200 p-2 sm:grid-cols-2">
                  {options.map((option) => (
                    <label key={option.code} className="flex items-center gap-2 text-sm text-slate-700">
                      <input
                        type="checkbox"
                        checked={field.value.includes(option.code)}
                        onChange={(event) => field.onChange(event.target.checked ? [...field.value, option.code] : field.value.filter((code) => code !== option.code))}
                      />
                      {option.name}
                    </label>
                  ))}
                  {types.isLoading && <span className="text-xs text-slate-500">Loading the list of checks...</span>}
                </div>
              </fieldset>
            )
          }}
        />
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
