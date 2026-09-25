import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { useClients } from '../api'
import { type ReportInfoValues, reportInfoSchema } from '../schemas'
import type { CaseView } from '../types'
import { useReportDirty } from './dirtyGuard'
import { SectionShell } from './SectionShell'
import type { SectionProps } from './sectionProps'
import { useSectionSaving } from './useSectionSaving'

function toValues(c: CaseView): ReportInfoValues {
  return {
    reportId: c.reportId,
    issueDate: c.issueDate,
    clientId: c.client.id,
    companyDisplayName: c.companyDisplayName ?? '',
    dueDate: c.dueDate ?? '',
  }
}

/** Section 1: Report ID, issue date, client, and the company name as it prints on the report. */
export function ReportInfoSection({ caseView, canEdit, onReload }: SectionProps) {
  const saving = useSectionSaving(caseView, 'report-info')
  const clients = useClients()
  const form = useForm<ReportInfoValues>({ resolver: zodResolver(reportInfoSchema), defaultValues: toValues(caseView) })
  const { errors, isDirty } = form.formState

  useEffect(() => form.reset(toValues(caseView)), [caseView.version]) // eslint-disable-line
  useReportDirty(isDirty)

  // A client that has since been deactivated stays selectable on the case that already uses it.
  const options = (clients.data ?? []).filter((client) => client.active || client.id === caseView.client.id)

  const submit = form.handleSubmit(async (values) => {
    await saving.save({
      reportId: values.reportId.trim(),
      issueDate: values.issueDate,
      clientId: values.clientId,
      companyDisplayName: values.companyDisplayName.trim() || null,
      dueDate: values.dueDate || null,
    })
  })

  return (
    <SectionShell
      title="1. Report info"
      description="The Report ID is generated for you and can be changed. It must be unique."
      canEdit={canEdit}
      saving={saving}
      onSubmit={submit}
      onReload={onReload}
      onEdit={saving.clearFeedback}
    >
      <fieldset disabled={!canEdit} className="grid gap-4 md:grid-cols-2">
        <Field label="Report ID" htmlFor="ri-report-id" required error={errors.reportId?.message}>
          <Input id="ri-report-id" aria-invalid={!!errors.reportId} {...form.register('reportId')} />
        </Field>
        <Field label="Issue date" htmlFor="ri-issue-date" required error={errors.issueDate?.message}>
          <Input id="ri-issue-date" type="date" aria-invalid={!!errors.issueDate} {...form.register('issueDate')} />
        </Field>
        <Field label="Client" htmlFor="ri-client" required error={errors.clientId?.message}>
          <Select id="ri-client" aria-invalid={!!errors.clientId} {...form.register('clientId')}>
            {options.length === 0 && <option value={caseView.client.id}>{caseView.client.name}</option>}
            {options.map((client) => (
              <option key={client.id} value={client.id}>
                {client.name}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Due date (optional)" htmlFor="ri-due-date" error={errors.dueDate?.message}>
          <Input id="ri-due-date" type="date" {...form.register('dueDate')} />
        </Field>
        <div className="md:col-span-2">
          <Field
            label="Company name on the report"
            htmlFor="ri-company"
            hint={`Leave empty to print the client's name (${caseView.client.displayName.replace(/\n/g, ', ')}). Line breaks are kept.`}
            error={errors.companyDisplayName?.message}
          >
            <Textarea id="ri-company" rows={3} {...form.register('companyDisplayName')} />
          </Field>
        </div>
      </fieldset>
    </SectionShell>
  )
}
