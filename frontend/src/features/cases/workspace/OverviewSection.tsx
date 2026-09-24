import { useEffect } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { type OverviewValues, overviewSchema } from '../schemas'
import type { CaseView, StatusPreset } from '../types'
import { useReportDirty } from './dirtyGuard'
import { PRESETS } from './presets'
import { SectionShell } from './SectionShell'
import type { SectionProps } from './sectionProps'
import { useSectionSaving } from './useSectionSaving'

function toValues(c: CaseView): OverviewValues {
  const o = c.overview
  return {
    statusPreset: o.statusPreset,
    statusTitle: o.statusTitle,
    statusSubtitle: o.statusSubtitle,
    totalOverride: o.totalOverride === null ? '' : String(o.totalOverride),
    completedOverride: o.completedOverride === null ? '' : String(o.completedOverride),
    overallStatusOverride: o.overallStatusOverride ?? '',
  }
}

/** Section 5: the status pill, and the overview numbers (automatic, with optional manual override). */
export function OverviewSection({ caseView, canEdit, onReload }: SectionProps) {
  const saving = useSectionSaving(caseView, 'overview')
  const form = useForm<OverviewValues>({ resolver: zodResolver(overviewSchema), defaultValues: toValues(caseView) })
  const { errors, isDirty } = form.formState
  const preset = useWatch({ control: form.control, name: 'statusPreset' })
  const title = useWatch({ control: form.control, name: 'statusTitle' })
  const subtitle = useWatch({ control: form.control, name: 'statusSubtitle' })
  const auto = caseView.overview.auto

  useEffect(() => form.reset(toValues(caseView)), [caseView.version]) // eslint-disable-line

  // Choosing a preset fills in its standard wording, which can still be edited. All four radios share
  // one field, so the choice is read from the event rather than from a per-radio handler.
  const choosePreset = (chosen: StatusPreset) => {
    form.setValue('statusTitle', PRESETS[chosen].title, { shouldDirty: true })
    form.setValue('statusSubtitle', PRESETS[chosen].subtitle, { shouldDirty: true })
  }
  useReportDirty(isDirty)

  const submit = form.handleSubmit(async (values) => {
    const whole = (text: string) => (text.trim() === '' ? null : Number(text.trim()))
    await saving.save({
      statusPreset: values.statusPreset,
      statusTitle: values.statusTitle.trim(),
      statusSubtitle: values.statusSubtitle.trim(),
      totalOverride: whole(values.totalOverride),
      completedOverride: whole(values.completedOverride),
      overallStatusOverride: values.overallStatusOverride.trim() || null,
    })
  })

  return (
    <SectionShell
      title="5. Overview & status"
      description="The status pill on page 1, and the totals shown in the report overview."
      canEdit={canEdit}
      saving={saving}
      onSubmit={submit}
      onReload={onReload}
      onEdit={saving.clearFeedback}
    >
      <fieldset disabled={!canEdit} className="flex flex-col gap-4">
        <fieldset
          className="grid gap-2 md:grid-cols-2"
          onChange={(event) => {
            const target = event.target
            if (target instanceof HTMLInputElement && target.name === 'statusPreset') {
              choosePreset(target.value as StatusPreset)
            }
          }}
        >
          <legend className="mb-1 text-sm font-medium text-slate-700">Status</legend>
          {(Object.keys(PRESETS) as StatusPreset[]).map((key) => (
            <label key={key} className={`flex cursor-pointer items-center gap-2 rounded-md border p-2 text-sm ${PRESETS[key].colours}`}>
              <input
                type="radio"
                value={key}
                {...form.register('statusPreset')}
              />
              {PRESETS[key].title}
            </label>
          ))}
        </fieldset>
        <div className="grid gap-4 md:grid-cols-2">
          <Field label="Status title" htmlFor="ov-title" error={errors.statusTitle?.message}>
            <Input id="ov-title" {...form.register('statusTitle')} />
          </Field>
          <Field label="Status subtitle" htmlFor="ov-subtitle" error={errors.statusSubtitle?.message}>
            <Input id="ov-subtitle" {...form.register('statusSubtitle')} />
          </Field>
        </div>
        <div aria-label="Status preview" className={`rounded-md border p-3 text-center ${PRESETS[preset].colours}`}>
          <div className="font-semibold">{title || PRESETS[preset].title}</div>
          <div className="text-sm">{subtitle || PRESETS[preset].subtitle}</div>
        </div>

        <div>
          <h3 className="mb-1 text-sm font-medium text-slate-700">Report overview</h3>
          <p className="mb-2 text-xs text-slate-500">
            Worked out from the checks: {auto.total} total, {auto.completed} completed, overall status &ldquo;{auto.overallStatus}&rdquo;.
            Type a value to override it; leave a box empty to use the automatic one.
          </p>
          <div className="grid gap-4 md:grid-cols-3">
            <Field label="Total verifications" htmlFor="ov-total" error={errors.totalOverride?.message}>
              <Input id="ov-total" inputMode="numeric" placeholder={String(auto.total)} {...form.register('totalOverride')} />
            </Field>
            <Field label="Completed" htmlFor="ov-completed" error={errors.completedOverride?.message}>
              <Input id="ov-completed" inputMode="numeric" placeholder={String(auto.completed)} {...form.register('completedOverride')} />
            </Field>
            <Field label="Overall status" htmlFor="ov-overall" error={errors.overallStatusOverride?.message}>
              <Input id="ov-overall" placeholder={auto.overallStatus} {...form.register('overallStatusOverride')} />
            </Field>
          </div>
        </div>
      </fieldset>
    </SectionShell>
  )
}
