import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { type PeriodValues, periodSchema } from '../schemas'
import type { CaseView } from '../types'
import { useReportDirty } from './dirtyGuard'
import { SectionShell } from './SectionShell'
import type { SectionProps } from './sectionProps'
import { useFieldIssues } from './useFieldIssues'
import { useSectionSaving } from './useSectionSaving'

function toValues(c: CaseView): PeriodValues {
  return { show: c.period.show, start: c.period.start ?? '', end: c.period.end ?? '' }
}

/** Section 3: the period the verification covers, and whether page 1 of the report shows it. */
export function PeriodSection({ caseView, canEdit, onReload }: SectionProps) {
  const saving = useSectionSaving(caseView, 'verification-period')
  const form = useForm<PeriodValues>({ resolver: zodResolver(periodSchema), defaultValues: toValues(caseView) })
  const { errors, isDirty, dirtyFields } = form.formState
  const issueFor = useFieldIssues(caseView.id, 'verification-period')

  useEffect(() => form.reset(toValues(caseView)), [caseView.version]) // eslint-disable-line
  useReportDirty(isDirty)

  const submit = form.handleSubmit(async (values) => {
    await saving.save({ show: values.show, start: values.start || null, end: values.end || null })
  })

  return (
    <SectionShell
      title="3. Verification period"
      description="Hiding the period keeps the dates but removes the row from page 1 of the report."
      canEdit={canEdit}
      saving={saving}
      onSubmit={submit}
      onReload={onReload}
      onEdit={saving.clearFeedback}
    >
      <fieldset disabled={!canEdit} className="grid gap-4 md:grid-cols-2">
        <label className="flex items-center gap-2 text-sm text-slate-700 md:col-span-2">
          <input type="checkbox" {...form.register('show')} /> Show the verification period on the report
        </label>
        <Field label="Start date" htmlFor="vp-start" error={errors.start?.message} issue={issueFor('start', !!dirtyFields.start || !!dirtyFields.show)}>
          <Input id="vp-start" type="date" {...form.register('start')} />
        </Field>
        <Field label="End date" htmlFor="vp-end" error={errors.end?.message} issue={issueFor('end', !!dirtyFields.end || !!dirtyFields.show)}>
          <Input id="vp-end" type="date" aria-invalid={!!errors.end} {...form.register('end')} />
        </Field>
      </fieldset>
    </SectionShell>
  )
}
