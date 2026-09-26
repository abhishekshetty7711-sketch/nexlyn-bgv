import { useState } from 'react'
import { Controller, useFieldArray, useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { ApiError } from '@/api/httpClient'
import { describeError } from '@/api/errors'
import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { DateField } from '@/components/ui/date-input'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { useAuth } from '@/features/auth/AuthContext'
import type { CaseView } from '../types'
import { BoldTextEditor } from '../workspace/BoldTextEditor'
import { useReportDirty } from '../workspace/dirtyGuard'
import { useFieldIssues } from '../workspace/useFieldIssues'
import { useSaveCheck } from './api'
import { buildCheckSchema, type CheckFormValues, toFormValues, toSaveInput } from './checkForm'
import { FieldInput } from './FieldInput'
import { CheckDocuments } from '../../documents/CheckDocuments'
import { FreeSections } from './FreeSections'
import { type CheckTypeDef, type CheckView, type DateSync, STATUS_LABELS, STATUS_MARKS, STATUS_ORDER } from './types'

const SYNC_BADGES: Record<DateSync, { text: string; hint: string }> = {
  MASTER: { text: '★ Master', hint: 'These dates come from this first check and fill in the other checks.' },
  AUTO: { text: '🔄 Auto', hint: "These dates follow the first check's dates." },
  MANUAL: { text: '✏️ Manual', hint: 'These dates were set by hand on this check and are not overwritten.' },
}

interface CheckEditorProps {
  caseView: CaseView
  check: CheckView
  def: CheckTypeDef
  /** False when the case is locked or the admin may not change checks. */
  canEdit: boolean
}

/**
 * One check, edited as a whole (CLAUDE.md section 7, section 4 A-G). The form is built from the type's
 * definition. Keyed by check id; after a save the form is reset to what the server returned.
 */
export function CheckEditor({ caseView, check, def, canEdit }: CheckEditorProps) {
  const { hasPermission } = useAuth()
  const [schema] = useState(() => buildCheckSchema(def))
  const form = useForm<CheckFormValues>({ resolver: zodResolver(schema), defaultValues: toFormValues(check) })
  const { errors, isDirty, dirtyFields } = form.formState
  const attesting = useWatch({ control: form.control, name: 'hasAttestation' })
  const details = useFieldArray({ control: form.control, name: 'details' })
  const [follow, setFollow] = useState<ReadonlySet<string>>(new Set())
  const [message, setMessage] = useState<{ kind: 'saved' | 'error' | 'conflict'; text: string } | null>(null)
  const save = useSaveCheck(caseView.id, check.id)
  const issueFor = useFieldIssues(caseView.id, 'checks', `check:${check.id}:`)

  useReportDirty(isDirty || follow.size > 0)

  const canAttest = hasPermission('ATTESTATION_APPLY')
  const canReveal = hasPermission('PII_UNMASK')
  const stored = new Map(check.fields.map((field) => [field.key, field]))
  const badge = SYNC_BADGES[check.dateSync]

  const submit = form.handleSubmit(async (values) => {
    setMessage(null)
    const editedKeys = new Set(Object.entries(dirtyFields.fields ?? {}).filter(([, value]) => value?.value).map(([key]) => key))
    try {
      const saved = await save.mutateAsync(toSaveInput(values, { check, def, editedKeys, followCandidateKeys: follow }))
      form.reset(toFormValues(saved)) // start again from what the server now holds (typed-in numbers are dropped)
      setFollow(new Set())
      setMessage({ kind: 'saved', text: 'Saved' })
    } catch (problem) {
      setMessage({ kind: problem instanceof ApiError && problem.status === 409 ? 'conflict' : 'error', text: describeError(problem) })
    }
  })

  return (
    <div className="flex flex-col gap-4">
      <form className="flex flex-col gap-4" onSubmit={submit} onChange={() => setMessage(null)} noValidate aria-label={`Edit ${check.title}`}>
        {/* The check form is long (about 3,000 px): the title and Save stay in view while it scrolls, like the other sections' Save bar. */}
        <div className="sticky top-13 z-10 -mx-4 flex items-start justify-between gap-4 border-b border-line bg-white/95 px-4 py-3 backdrop-blur lg:top-0">
          <div>
            <h3 className="text-base font-semibold text-slate-900">{check.displayName}</h3>
            <p className="text-sm text-slate-500">Document: {check.documentName}</p>
          </div>
          {canEdit && (
            <div className="flex items-center gap-3">
              {message?.kind === 'saved' && (
                <span role="status" className="text-sm text-emerald-700">
                  Saved
                </span>
              )}
              <Button type="submit" disabled={save.isPending}>
                {save.isPending ? 'Saving...' : 'Save check'}
              </Button>
            </div>
          )}
        </div>

        {message && message.kind !== 'saved' && (
          <Alert variant="error">
            {message.text}
            {message.kind === 'conflict' && ' Reload the case to see the latest version, then try again.'}
          </Alert>
        )}
        {Object.keys(errors).length > 0 && <Alert variant="error">Some values need fixing. They are marked below.</Alert>}

        <fieldset disabled={!canEdit} className="flex flex-col gap-6">
          {/* A. Card info */}
          <section className="grid gap-4 md:grid-cols-2" aria-label="Card information">
            <div className="md:col-span-2">
              <Field label="Title" htmlFor="ck-title" error={errors.title?.message}>
                <Input id="ck-title" aria-invalid={!!errors.title} {...form.register('title')} />
              </Field>
            </div>
            <Field label="Summary description (page 1)" htmlFor="ck-summary" error={errors.summaryDescription?.message}>
              <Textarea id="ck-summary" rows={2} {...form.register('summaryDescription')} />
            </Field>
            <Field label="This card verifies (detail page)" htmlFor="ck-verifies" hint={`Leave blank to use "${check.documentName}".`} error={errors.thisCardVerifies?.message}>
              <Textarea id="ck-verifies" rows={2} {...form.register('thisCardVerifies')} />
            </Field>
            <Field label="Status" htmlFor="ck-status" issue={issueFor('status', !!dirtyFields.status)}>
              <Select id="ck-status" {...form.register('status')}>
                {STATUS_ORDER.map((status) => (
                  <option key={status} value={status}>
                    {STATUS_MARKS[status]} {STATUS_LABELS[status]}
                  </option>
                ))}
              </Select>
            </Field>
            <Field label="Verification type" htmlFor="ck-vtype" error={errors.verificationType?.message}>
              <Input id="ck-vtype" {...form.register('verificationType')} />
            </Field>
            <Field label="Requested date" htmlFor="ck-requested" error={errors.requestedDate?.message} issue={issueFor('requestedDate', !!dirtyFields.requestedDate)}>
              <DateField control={form.control} name="requestedDate" id="ck-requested" />
            </Field>
            <Field label="Completed date" htmlFor="ck-completed" error={errors.completedDate?.message} issue={issueFor('completedDate', !!dirtyFields.completedDate)}>
              <DateField control={form.control} name="completedDate" id="ck-completed" />
            </Field>
            <p className="text-xs text-slate-500 md:col-span-2" title={badge.hint}>
              Dates: <Badge>{badge.text}</Badge> {badge.hint}
            </p>
          </section>

          {/* B. Type-specific fields */}
          <section className="flex flex-col gap-3" aria-label="Verification details">
            <h4 className="text-sm font-semibold text-slate-800">Details checked</h4>
            {def.fields.map((fieldDef) => (
              <FieldInput
                key={fieldDef.key}
                def={fieldDef}
                stored={stored.get(fieldDef.key)}
                form={form}
                caseId={caseView.id}
                checkId={check.id}
                disabled={!canEdit}
                canReveal={canReveal}
                parentType={caseView.candidate.parentType}
                issue={issueFor(fieldDef.label, !!dirtyFields.fields?.[fieldDef.key])}
                followsCandidate={follow.has(fieldDef.key)}
                onFollowCandidate={(key) => setFollow((current) => new Set(current).add(key))}
              />
            ))}
          </section>

          {/* C. Extra details */}
          <section className="flex flex-col gap-2" aria-label="Extra details">
            <h4 className="text-sm font-semibold text-slate-800">Extra details</h4>
            {details.fields.map((row, index) => (
              <div key={row.id} className="flex items-end gap-2">
                <Field label={`Detail ${index + 1} label`} htmlFor={`ck-detail-label-${index}`} error={errors.details?.[index]?.label?.message}>
                  <Input id={`ck-detail-label-${index}`} {...form.register(`details.${index}.label`)} />
                </Field>
                <Field label={`Detail ${index + 1} value`} htmlFor={`ck-detail-value-${index}`} error={errors.details?.[index]?.value?.message}>
                  <Input id={`ck-detail-value-${index}`} {...form.register(`details.${index}.value`)} />
                </Field>
                <Button type="button" size="sm" variant="ghost" onClick={() => details.remove(index)} aria-label={`Remove detail ${index + 1}`}>
                  Remove
                </Button>
              </div>
            ))}
            <div>
              <Button type="button" size="sm" variant="outline" onClick={() => details.append({ label: '', value: '' })}>
                Add detail
              </Button>
            </div>
          </section>

          {/* F. Remarks */}
          <Controller
            control={form.control}
            name="remarks"
            render={({ field }) => (
              <BoldTextEditor id="ck-remarks" label="Remarks for this check" value={field.value} onChange={field.onChange} disabled={!canEdit} error={errors.remarks?.message} />
            )}
          />

          {/* F2. Where the comments print */}
          <div className="flex flex-col gap-1">
            <label className="flex items-center gap-2 text-sm font-medium text-slate-700">
              <input type="checkbox" {...form.register('commentsOnNextPage')} />
              Print the comments (and the attestation) on a page of their own
            </label>
            <p className="ml-6 text-xs text-slate-600">
              Puts them on a "— Continued" page straight after this check&apos;s main page, before any document that has its own page. Use it when
              long comments would crowd the page. Nothing changes if there are no comments and no attestation.
            </p>
          </div>

          {/* G. Attestation */}
          <section className="flex flex-col gap-2" aria-label="Legal attestation">
            <label className="flex items-center gap-2 text-sm font-medium text-slate-700">
              <input type="checkbox" disabled={!canAttest} {...form.register('hasAttestation')} />
              Add the advocate&apos;s legal attestation to this check
            </label>
            {!canAttest && <p className="text-xs text-slate-500">Only people with the attestation permission can turn this on or off.</p>}
            {attesting && (
              <div className="grid gap-4 md:grid-cols-2">
                <Field label="Bar Council number" htmlFor="ck-bar" error={errors.barCouncilNo?.message}>
                  <Input id="ck-bar" disabled={!canAttest} {...form.register('barCouncilNo')} />
                </Field>
                <div className="md:col-span-2">
                  <Field label="Disclaimer" htmlFor="ck-disclaimer" error={errors.disclaimer?.message}>
                    <Textarea id="ck-disclaimer" rows={4} disabled={!canAttest} {...form.register('disclaimer')} />
                  </Field>
                </div>
              </div>
            )}
          </section>
        </fieldset>
      </form>

      {/* D. Documents and E. free blocks save on their own, so they sit outside the form. */}
      <CheckDocuments
        caseId={caseView.id}
        checkId={check.id}
        canUpload={caseView.editable && hasPermission('DOCUMENT_UPLOAD')}
        canDelete={caseView.editable && hasPermission('DOCUMENT_DELETE')}
      />
      <FreeSections caseId={caseView.id} check={check} canEdit={canEdit} />
    </div>
  )
}
