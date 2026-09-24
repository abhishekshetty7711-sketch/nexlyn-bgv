import { useEffect } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { formatDate } from '../format'
import { type SettingsValues, settingsSchema, todayIso } from '../schemas'
import type { CaseView } from '../types'
import { useReportDirty } from './dirtyGuard'
import { SectionShell } from './SectionShell'
import type { SectionProps } from './sectionProps'
import { useSectionSaving } from './useSectionSaving'

function toValues(c: CaseView): SettingsValues {
  const s = c.settings
  return {
    layoutCards: s.layoutCards === 6 ? '6' : '4',
    dateFormat: s.dateFormat,
    watermarkEnabled: s.watermarkEnabled,
    watermarkText: s.watermarkText,
  }
}

/** Section 7: how the report is laid out and how dates and the watermark print. */
export function SettingsSection({ caseView, canEdit, onReload }: SectionProps) {
  const saving = useSectionSaving(caseView, 'settings')
  const form = useForm<SettingsValues>({ resolver: zodResolver(settingsSchema), defaultValues: toValues(caseView) })
  const { errors, isDirty } = form.formState
  const watermarkOn = useWatch({ control: form.control, name: 'watermarkEnabled' })
  const today = todayIso()

  useEffect(() => form.reset(toValues(caseView)), [caseView.version]) // eslint-disable-line
  useReportDirty(isDirty)

  const submit = form.handleSubmit(async (values) => {
    await saving.save({
      layoutCards: Number(values.layoutCards),
      dateFormat: values.dateFormat,
      watermarkEnabled: values.watermarkEnabled,
      watermarkText: values.watermarkText.trim(),
    })
  })

  return (
    <SectionShell
      title="7. Report settings"
      canEdit={canEdit}
      saving={saving}
      onSubmit={submit}
      onReload={onReload}
      onEdit={saving.clearFeedback}
    >
      <fieldset disabled={!canEdit} className="flex flex-col gap-5">
        <fieldset className="flex flex-col gap-1">
          <legend className="mb-1 text-sm font-medium text-slate-700">Cards on page 1</legend>
          <label className="flex items-center gap-2 text-sm">
            <input type="radio" value="4" {...form.register('layoutCards')} /> 4 cards (spacious, default)
          </label>
          <label className="flex items-center gap-2 text-sm">
            <input type="radio" value="6" {...form.register('layoutCards')} /> 6 cards (compact)
          </label>
        </fieldset>
        <fieldset className="flex flex-col gap-1">
          <legend className="mb-1 text-sm font-medium text-slate-700">Date format</legend>
          <label className="flex items-center gap-2 text-sm">
            <input type="radio" value="NUMERIC" {...form.register('dateFormat')} /> Numeric, for example {formatDate(today, 'NUMERIC')}
          </label>
          <label className="flex items-center gap-2 text-sm">
            <input type="radio" value="TEXT" {...form.register('dateFormat')} /> Text, for example {formatDate(today, 'TEXT')}
          </label>
        </fieldset>
        <div className="flex flex-col gap-2">
          <label className="flex items-center gap-2 text-sm text-slate-700">
            <input type="checkbox" {...form.register('watermarkEnabled')} /> Print a diagonal watermark on every page
          </label>
          <Field label="Watermark text" htmlFor="st-watermark" hint="At most 40 characters." error={errors.watermarkText?.message}>
            <Input id="st-watermark" disabled={!watermarkOn} aria-invalid={!!errors.watermarkText} {...form.register('watermarkText')} />
          </Field>
        </div>
      </fieldset>
    </SectionShell>
  )
}
