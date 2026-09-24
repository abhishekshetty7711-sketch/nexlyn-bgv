import { useEffect } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { type RemarksValues, remarksSchema } from '../schemas'
import type { CaseView } from '../types'
import { BoldTextEditor } from './BoldTextEditor'
import { useReportDirty } from './dirtyGuard'
import { SectionShell } from './SectionShell'
import type { SectionProps } from './sectionProps'
import { useSectionSaving } from './useSectionSaving'

function toValues(c: CaseView): RemarksValues {
  return { analystRemarks: c.remarks.analystRemarks ?? '', finalRecommendation: c.remarks.finalRecommendation ?? '' }
}

/** Section 6: the analyst's remarks and the final recommendation (bold formatting only). */
export function RemarksSection({ caseView, canEdit, onReload }: SectionProps) {
  const saving = useSectionSaving(caseView, 'remarks')
  const form = useForm<RemarksValues>({ resolver: zodResolver(remarksSchema), defaultValues: toValues(caseView) })
  const { errors, isDirty } = form.formState

  useEffect(() => form.reset(toValues(caseView)), [caseView.version]) // eslint-disable-line
  useReportDirty(isDirty)

  const submit = form.handleSubmit(async (values) => {
    await saving.save({
      analystRemarks: values.analystRemarks.trim() || null,
      finalRecommendation: values.finalRecommendation.trim() || null,
    })
  })

  return (
    <SectionShell
      title="6. Remarks & recommendation"
      description="Use the B button for bold. Nothing else is allowed in these boxes."
      canEdit={canEdit}
      saving={saving}
      onSubmit={submit}
      onReload={onReload}
      onEdit={saving.clearFeedback}
    >
      <Controller
        control={form.control}
        name="analystRemarks"
        render={({ field }) => (
          <BoldTextEditor
            id="rm-remarks"
            label="Analyst remarks"
            value={field.value}
            onChange={(next) => field.onChange(next)}
            disabled={!canEdit}
            error={errors.analystRemarks?.message}
          />
        )}
      />
      <Controller
        control={form.control}
        name="finalRecommendation"
        render={({ field }) => (
          <BoldTextEditor
            id="rm-recommendation"
            label="Final recommendation"
            value={field.value}
            onChange={(next) => field.onChange(next)}
            disabled={!canEdit}
            error={errors.finalRecommendation?.message}
          />
        )}
      />
    </SectionShell>
  )
}
