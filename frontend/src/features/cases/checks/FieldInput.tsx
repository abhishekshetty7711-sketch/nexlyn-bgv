import { useEffect, useRef, useState } from 'react'
import { type UseFormReturn, useWatch } from 'react-hook-form'
import { describeError } from '@/api/errors'
import { Button } from '@/components/ui/button'
import { Field, type FieldIssue } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { revealField } from './api'
import type { CheckFormValues } from './checkForm'
import type { CheckFieldView, FieldDef, ItemFieldDef } from './types'
import { parseRows, stringifyRows } from './validation'

/** How long a revealed Aadhaar / PAN / UAN stays on screen (CLAUDE.md section 11.5). */
export const REVEAL_SECONDS = 30

interface FieldInputProps {
  def: FieldDef
  stored: CheckFieldView | undefined
  form: UseFormReturn<CheckFormValues>
  caseId: string
  checkId: string
  disabled: boolean
  canReveal: boolean
  /** The candidate's "related person" label switches between Father and Guardian. */
  parentType: 'FATHER' | 'GUARDIAN'
  followsCandidate: boolean
  onFollowCandidate: (key: string) => void
  /** What the case checklist says about this field (for example a required number that is missing). */
  issue?: FieldIssue
}

const SOURCE_LABELS = { CANDIDATE: 'From candidate', MANUAL: 'Entered by hand', API: 'From verification API' } as const

function labelFor(def: FieldDef, parentType: 'FATHER' | 'GUARDIAN') {
  return def.labelByParentType && parentType === 'GUARDIAN' ? def.label.replace(/^Father/, 'Guardian') : def.label
}

/** One field of a check, drawn from its definition: input by kind, verified tick and source badge. */
export function FieldInput({ def, stored, form, caseId, checkId, disabled, canReveal, parentType, followsCandidate, onFollowCandidate, issue }: FieldInputProps) {
  const { errors } = form.formState
  const fieldErrors = errors.fields?.[def.key]
  const error = fieldErrors?.value?.message ?? fieldErrors?.replacement?.message ?? fieldErrors?.root?.message
  const label = labelFor(def, parentType)
  const id = `ck-${checkId}-${def.key}`
  const isPrefilled = def.prefill !== null && !def.sensitive
  const manual = stored?.manual === true && !followsCandidate

  return (
    <div className="flex flex-col gap-1 rounded-md border border-slate-200 p-3">
      <div className="flex items-center justify-between gap-2">
        <div className="flex flex-wrap items-center gap-2">
          {isPrefilled && (
            <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-600">
              {followsCandidate ? 'Will follow the candidate' : manual ? SOURCE_LABELS.MANUAL : SOURCE_LABELS.CANDIDATE}
            </span>
          )}
          {isPrefilled && manual && !disabled && (
            <Button type="button" size="sm" variant="ghost" onClick={() => onFollowCandidate(def.key)}>
              Use candidate value
            </Button>
          )}
        </div>
        <label className="flex items-center gap-1 text-xs text-slate-600">
          <input type="checkbox" disabled={disabled} aria-label={`${label} verified`} {...form.register(`fields.${def.key}.verifiedTick`)} />
          Verified
        </label>
      </div>

      {def.sensitive ? (
        <SensitiveInput
          def={def}
          label={label}
          id={id}
          stored={stored}
          form={form}
          caseId={caseId}
          checkId={checkId}
          disabled={disabled}
          canReveal={canReveal}
          error={error}
          issue={issue}
        />
      ) : def.type === 'repeatable' ? (
        <RowsEditor def={def} label={label} form={form} disabled={disabled} error={error} issue={issue} />
      ) : (
        <Field label={label} htmlFor={id} required={def.required} error={error} issue={issue}>
          <PlainInput def={def} id={id} form={form} disabled={disabled} hasError={!!error} />
        </Field>
      )}
    </div>
  )
}

function PlainInput({ def, id, form, disabled, hasError }: { def: FieldDef; id: string; form: UseFormReturn<CheckFormValues>; disabled: boolean; hasError: boolean }) {
  const registration = form.register(`fields.${def.key}.value`)
  if (def.type === 'textarea') {
    return <Textarea id={id} disabled={disabled} aria-invalid={hasError} {...registration} />
  }
  if (def.type === 'date') {
    return <Input id={id} type="date" disabled={disabled} aria-invalid={hasError} {...registration} />
  }
  if (def.type === 'boolean') {
    return (
      <Select id={id} disabled={disabled} aria-invalid={hasError} {...registration}>
        <option value="">Not set</option>
        <option value="Yes">Yes</option>
        <option value="No">No</option>
      </Select>
    )
  }
  if (def.type === 'select') {
    return (
      <Select id={id} disabled={disabled} aria-invalid={hasError} {...registration}>
        <option value="">Not set</option>
        {def.options.map((option) => (
          <option key={option} value={option}>
            {option}
          </option>
        ))}
      </Select>
    )
  }
  return <Input id={id} autoComplete="off" disabled={disabled} aria-invalid={hasError} {...registration} />
}

interface SensitiveProps {
  def: FieldDef
  label: string
  id: string
  stored: CheckFieldView | undefined
  form: UseFormReturn<CheckFormValues>
  caseId: string
  checkId: string
  disabled: boolean
  canReveal: boolean
  error?: string
  issue?: FieldIssue
}

/**
 * An Aadhaar / PAN / UAN number. Only the masked form is ever loaded. "Reveal" fetches the real one
 * (audited by the server), keeps it in this component's state only, and hides it after 30 seconds.
 * Typing in the box replaces the stored number; the old one is never put back into the form.
 */
function SensitiveInput({ def, label, id, stored, form, caseId, checkId, disabled, canReveal, error, issue }: SensitiveProps) {
  const [revealed, setRevealed] = useState<string | null>(null)
  const [revealError, setRevealError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const clearing = useWatch({ control: form.control, name: `fields.${def.key}.clear` })

  useEffect(
    () => () => {
      if (timer.current) {
        clearTimeout(timer.current)
      }
    },
    [],
  )

  async function reveal() {
    setLoading(true)
    setRevealError(null)
    try {
      const result = await revealField(caseId, checkId, def.key)
      setRevealed(result.value)
      if (timer.current) {
        clearTimeout(timer.current)
      }
      timer.current = setTimeout(() => setRevealed(null), REVEAL_SECONDS * 1000)
    } catch (problem) {
      setRevealError(describeError(problem))
    } finally {
      setLoading(false)
    }
  }

  const hasStored = stored?.hasValue === true

  return (
    <div className="flex flex-col gap-2">
      <span className="text-sm font-medium text-slate-700">
        {label}
        {def.required && <span className="font-normal text-slate-600"> (required)</span>}
      </span>
      <div className="flex flex-wrap items-center gap-2 text-sm">
        {hasStored ? (
          <>
            <span data-testid={`${def.key}-current`} className={clearing ? 'font-mono text-slate-500 line-through' : 'font-mono text-slate-900'}>
              {revealed ?? stored?.value}
            </span>
            {revealed === null ? (
              canReveal && (
                <Button type="button" size="sm" variant="outline" disabled={loading} onClick={reveal} aria-label={`Reveal ${label}`}>
                  {loading ? 'Loading...' : 'Reveal'}
                </Button>
              )
            ) : (
              <Button type="button" size="sm" variant="outline" onClick={() => setRevealed(null)} aria-label={`Hide ${label}`}>
                Hide
              </Button>
            )}
            {revealed !== null && <span className="text-xs text-slate-500">Hides itself after {REVEAL_SECONDS} seconds.</span>}
          </>
        ) : (
          <span className="text-slate-500">Not entered yet</span>
        )}
      </div>
      {revealError && (
        <p className="text-xs text-red-600" role="alert">
          {revealError}
        </p>
      )}
      <Field label={hasStored ? `Replace ${label}` : `Enter ${label}`} htmlFor={id} error={error} issue={issue}>
        <Input
          id={id}
          autoComplete="off"
          disabled={disabled || clearing}
          aria-invalid={!!error}
          {...form.register(`fields.${def.key}.replacement`)}
        />
      </Field>
      {hasStored && !disabled && (
        <label className="flex items-center gap-2 text-xs text-slate-600">
          <input type="checkbox" {...form.register(`fields.${def.key}.clear`)} /> Remove the stored {label}
        </label>
      )}
    </div>
  )
}

/** A repeatable field (for example gap periods): rows of small inputs, stored as one JSON value. */
function RowsEditor({ def, label, form, disabled, error, issue }: { def: FieldDef; label: string; form: UseFormReturn<CheckFormValues>; disabled: boolean; error?: string; issue?: FieldIssue }) {
  // Rows are kept here (empty rows included) and written to the form as JSON; empty rows are dropped there.
  const [rows, setRows] = useState<Record<string, string>[]>(() => parseRows(form.getValues(`fields.${def.key}.value`)))

  function commit(next: Record<string, string>[]) {
    setRows(next)
    form.setValue(`fields.${def.key}.value`, stringifyRows(next), { shouldDirty: true, shouldValidate: form.formState.isSubmitted })
  }

  function setCell(index: number, column: ItemFieldDef, value: string) {
    commit(rows.map((row, i) => (i === index ? { ...row, [column.key]: value } : row)))
  }

  return (
    <fieldset className="flex flex-col gap-2" disabled={disabled}>
      <legend className="text-sm font-medium text-slate-700">
        {label}
        {def.required && <span className="font-normal text-slate-600"> (required)</span>}
      </legend>
      {rows.length === 0 && <p className="text-xs text-slate-500">No rows yet.</p>}
      {rows.map((row, index) => (
        <div key={index} className="grid gap-2 rounded-md bg-slate-50 p-2 md:grid-cols-[repeat(auto-fit,minmax(9rem,1fr))_auto]">
          {def.itemFields.map((column) => (
            <label key={column.key} className="flex flex-col gap-1 text-xs text-slate-600">
              {column.label}
              <Input
                type={column.type === 'date' ? 'date' : 'text'}
                aria-label={`${label} ${index + 1} ${column.label}`}
                value={row[column.key] ?? ''}
                onChange={(event) => setCell(index, column, event.target.value)}
              />
            </label>
          ))}
          <Button type="button" size="sm" variant="ghost" className="self-end" onClick={() => commit(rows.filter((_, i) => i !== index))} aria-label={`Remove ${label} row ${index + 1}`}>
            Remove
          </Button>
        </div>
      ))}
      <div>
        <Button type="button" size="sm" variant="outline" onClick={() => setRows([...rows, {}])}>
          Add row
        </Button>
      </div>
      {issue && !error && <p className={`text-xs font-medium ${issue.level === 'error' ? 'text-red-700' : 'text-amber-800'}`}>{issue.message}</p>}
      {error && (
        <p className="text-xs text-red-600" role="alert">
          {error}
        </p>
      )}
    </fieldset>
  )
}
