import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useNavigate } from 'react-router-dom'
import { describeError } from '@/api/errors'
import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { DateField } from '@/components/ui/date-input'
import { Field } from '@/components/ui/field'
import { Select } from '@/components/ui/select'
import { useClients, useCreateCase } from './api'
import { type NewCaseValues, newCaseSchema, todayIso } from './schemas'

/** Starts a case: choose the client, and the app generates the Report ID and opens the workspace. */
export function NewCaseDialog({ onClose }: { onClose: () => void }) {
  const navigate = useNavigate()
  const clients = useClients(true)
  const create = useCreateCase()
  const form = useForm<NewCaseValues>({
    resolver: zodResolver(newCaseSchema),
    defaultValues: { clientId: '', issueDate: todayIso(), dueDate: '' },
  })
  const { errors } = form.formState

  const submit = form.handleSubmit((values) =>
    create.mutate(
      { clientId: values.clientId, issueDate: values.issueDate || undefined, dueDate: values.dueDate || undefined },
      { onSuccess: (created) => navigate(`/cases/${created.id}`) },
    ),
  )

  return (
    <Dialog title="New case" onClose={onClose}>
      <form className="flex flex-col gap-3" onSubmit={submit} noValidate>
        {create.isError && <Alert variant="error">{describeError(create.error)}</Alert>}
        {clients.data && clients.data.length === 0 && (
          <Alert variant="info">There are no active clients yet. Add a client first.</Alert>
        )}
        <Field label="Client" htmlFor="nc-client" required error={errors.clientId?.message}>
          <Select id="nc-client" aria-invalid={!!errors.clientId} {...form.register('clientId')}>
            <option value="">Choose a client...</option>
            {clients.data?.map((client) => (
              <option key={client.id} value={client.id}>
                {client.name}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Issue date" htmlFor="nc-issue" hint="Defaults to today." error={errors.issueDate?.message}>
          <DateField control={form.control} name="issueDate" id="nc-issue" />
        </Field>
        <Field label="Due date (optional)" htmlFor="nc-due" error={errors.dueDate?.message}>
          <DateField control={form.control} name="dueDate" id="nc-due" />
        </Field>
        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={create.isPending}>
            Create case
          </Button>
        </div>
      </form>
    </Dialog>
  )
}
