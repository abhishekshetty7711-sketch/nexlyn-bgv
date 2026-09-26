import { useEffect } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { PhotoUploader } from '../../documents/PhotoUploader'
import { DateField } from '@/components/ui/date-input'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { type CandidateValues, candidateSchema } from '../schemas'
import type { CaseView } from '../types'
import { useReportDirty } from './dirtyGuard'
import { SectionShell } from './SectionShell'
import type { SectionProps } from './sectionProps'
import { useFieldIssues } from './useFieldIssues'
import { useSectionSaving } from './useSectionSaving'

function toValues(c: CaseView): CandidateValues {
  const k = c.candidate
  return {
    fullName: k.fullName ?? '',
    parentType: k.parentType,
    parentName: k.parentName ?? '',
    employeeId: k.employeeId ?? '',
    dob: k.dob ?? '',
    phone: k.phoneDisplay ?? k.phone ?? '',
    street: k.street ?? '',
    city: k.city ?? '',
    state: k.state ?? '',
    pin: k.pin ?? '',
    country: k.country,
  }
}

/** Section 2: the person being verified. Every check pre-fills from here. */
export function CandidateSection({ caseView, canEdit, onReload }: SectionProps) {
  const saving = useSectionSaving(caseView, 'candidate')
  const form = useForm<CandidateValues>({ resolver: zodResolver(candidateSchema), defaultValues: toValues(caseView) })
  const { errors, isDirty, dirtyFields } = form.formState
  const issueFor = useFieldIssues(caseView.id, 'candidate')
  const parentType = useWatch({ control: form.control, name: 'parentType' })

  useEffect(() => form.reset(toValues(caseView)), [caseView.version]) // eslint-disable-line
  useReportDirty(isDirty)

  const submit = form.handleSubmit(async (values) => {
    const orNull = (text: string) => text.trim() || null
    await saving.save({
      fullName: orNull(values.fullName),
      parentType: values.parentType,
      parentName: orNull(values.parentName),
      employeeId: orNull(values.employeeId),
      dob: values.dob || null,
      phone: orNull(values.phone),
      street: orNull(values.street),
      city: orNull(values.city),
      state: orNull(values.state),
      pin: orNull(values.pin),
      country: orNull(values.country),
    })
  })

  const parentLabel = parentType === 'GUARDIAN' ? "Guardian's name" : "Father's name"

  return (
    <SectionShell
      title="2. Candidate details"
      description="Checks pre-fill from these details, so fill them in first."
      canEdit={canEdit}
      saving={saving}
      onSubmit={submit}
      onReload={onReload}
      onEdit={saving.clearFeedback}
    >
      <fieldset disabled={!canEdit} className="grid gap-4 md:grid-cols-2">
        <div className="md:col-span-2">
          <Field label="Full name" htmlFor="cd-full-name" required error={errors.fullName?.message} issue={issueFor('fullName', !!dirtyFields.fullName)}>
            <Input id="cd-full-name" autoComplete="off" aria-invalid={!!errors.fullName} {...form.register('fullName')} />
          </Field>
        </div>
        <fieldset className="flex items-center gap-4 md:col-span-2">
          <legend className="mb-1 text-sm font-medium text-slate-700">Related person is the candidate&apos;s</legend>
          <label className="flex items-center gap-2 text-sm">
            <input type="radio" value="FATHER" {...form.register('parentType')} /> Father
          </label>
          <label className="flex items-center gap-2 text-sm">
            <input type="radio" value="GUARDIAN" {...form.register('parentType')} /> Guardian
          </label>
        </fieldset>
        <Field label={parentLabel} htmlFor="cd-parent-name" error={errors.parentName?.message} issue={issueFor('parentName', !!dirtyFields.parentName)}>
          <Input id="cd-parent-name" {...form.register('parentName')} />
        </Field>
        <Field label="Employee ID" htmlFor="cd-employee-id" required error={errors.employeeId?.message} issue={issueFor('employeeId', !!dirtyFields.employeeId)}>
          <Input id="cd-employee-id" {...form.register('employeeId')} />
        </Field>
        <Field label="Date of birth" htmlFor="cd-dob" error={errors.dob?.message} issue={issueFor('dob', !!dirtyFields.dob)}>
          <DateField control={form.control} name="dob" id="cd-dob" />
        </Field>
        <Field label="Phone" htmlFor="cd-phone" hint="Any way you would write it: 98765 43210 or +91 98765 43210." error={errors.phone?.message} issue={issueFor('phone', !!dirtyFields.phone)}>
          <Input id="cd-phone" inputMode="tel" aria-invalid={!!errors.phone} {...form.register('phone')} />
        </Field>
        <div className="md:col-span-2">
          <Field label="Street" htmlFor="cd-street" error={errors.street?.message}>
            <Input id="cd-street" {...form.register('street')} />
          </Field>
        </div>
        <Field label="City / town" htmlFor="cd-city" error={errors.city?.message}>
          <Input id="cd-city" {...form.register('city')} />
        </Field>
        <Field label="State" htmlFor="cd-state" error={errors.state?.message}>
          <Input id="cd-state" {...form.register('state')} />
        </Field>
        <Field label="PIN code" htmlFor="cd-pin" error={errors.pin?.message}>
          <Input id="cd-pin" inputMode="numeric" aria-invalid={!!errors.pin} {...form.register('pin')} />
        </Field>
        <Field label="Country" htmlFor="cd-country" error={errors.country?.message}>
          <Input id="cd-country" {...form.register('country')} />
        </Field>
      </fieldset>
      <PhotoUploader caseView={caseView} issue={issueFor('photo')} />
    </SectionShell>
  )
}
