import { z } from 'zod'
import { optionalDate } from '../schemas'
import type { SaveCheckInput } from './api'
import { type CheckStatus, type CheckTypeDef, type CheckView, STATUS_ORDER } from './types'
import { validateField } from './validation'

/** What one field looks like in the form. Sensitive fields never hold the real value: only a replacement. */
export interface FieldFormValue {
  value: string
  verifiedTick: boolean
  /** Sensitive fields only: a new number typed to replace the stored one. Empty = leave it as it is. */
  replacement: string
  /** Sensitive fields only: remove the stored number. */
  clear: boolean
}

export interface CheckFormValues {
  title: string
  summaryDescription: string
  thisCardVerifies: string
  status: CheckStatus
  verificationType: string
  requestedDate: string
  completedDate: string
  remarks: string
  hasAttestation: boolean
  barCouncilNo: string
  disclaimer: string
  commentsOnNextPage: boolean
  fields: Record<string, FieldFormValue>
  details: { label: string; value: string }[]
}

export function toFormValues(check: CheckView): CheckFormValues {
  const fields: Record<string, FieldFormValue> = {}
  for (const field of check.fields) {
    fields[field.key] = {
      // A sensitive field's stored value is only its masked form; it is shown separately, never edited here.
      value: field.sensitive ? '' : (field.value ?? ''),
      verifiedTick: field.verifiedTick,
      replacement: '',
      clear: false,
    }
  }
  return {
    title: check.title,
    summaryDescription: check.summaryDescription ?? '',
    thisCardVerifies: check.thisCardVerifies ?? '',
    status: check.status,
    verificationType: check.verificationType,
    requestedDate: check.requestedDate ?? '',
    completedDate: check.completedDate ?? '',
    remarks: check.remarks ?? '',
    hasAttestation: check.hasAttestation,
    barCouncilNo: check.barCouncilNo ?? '',
    disclaimer: check.disclaimer ?? '',
    commentsOnNextPage: check.commentsOnNextPage,
    fields,
    details: check.details.map((d) => ({ label: d.label, value: d.value ?? '' })),
  }
}

/** The validation rules for one check, built from its type definition (so a new field needs no code). */
export function buildCheckSchema(def: CheckTypeDef): z.ZodType<CheckFormValues, CheckFormValues> {
  const fieldShape: Record<string, z.ZodType<FieldFormValue>> = {}
  for (const field of def.fields) {
    fieldShape[field.key] = z
      .object({ value: z.string(), verifiedTick: z.boolean(), replacement: z.string(), clear: z.boolean() })
      .superRefine((value, ctx) => {
        const problem = validateField(field, field.sensitive ? value.replacement : value.value)
        if (problem) {
          ctx.addIssue({
            code: 'custom',
            path: [field.sensitive ? 'replacement' : 'value'],
            message: `${field.label} ${problem}`,
          })
        }
      })
  }
  const schema = z.object({
    title: z.string().trim().min(1, 'Enter a title').max(200, 'That title is too long'),
    summaryDescription: z.string().max(2000, 'That is too long'),
    thisCardVerifies: z.string().max(2000, 'That is too long'),
    status: z.enum(STATUS_ORDER as [CheckStatus, ...CheckStatus[]]),
    verificationType: z.string().max(50, 'That is too long'),
    requestedDate: optionalDate,
    completedDate: optionalDate,
    remarks: z.string().max(20000, 'That is too long'),
    hasAttestation: z.boolean(),
    barCouncilNo: z.string().max(50, 'That is too long'),
    disclaimer: z.string().max(5000, 'That is too long'),
    commentsOnNextPage: z.boolean(),
    fields: z.object(fieldShape),
    details: z.array(z.object({ label: z.string().max(200, 'Too long'), value: z.string().max(1000, 'Too long') })).max(30),
  })
  return schema as unknown as z.ZodType<CheckFormValues, CheckFormValues>
}

export interface RequestContext {
  check: CheckView
  def: CheckTypeDef
  /** Keys of prefilled fields whose value the admin edited in this form. */
  editedKeys: ReadonlySet<string>
  /** Keys of prefilled fields the admin asked to follow the candidate again. */
  followCandidateKeys: ReadonlySet<string>
}

/**
 * Builds the save request. The rules that matter (they mirror the server):
 * - a sensitive field is only sent when a replacement was typed (or it is being cleared);
 * - a prefilled field that was edited is sent as manual; one left alone keeps following the candidate;
 * - "use candidate value" sends manual = false, which makes the server take the candidate's value again.
 */
export function toSaveInput(values: CheckFormValues, context: RequestContext): SaveCheckInput {
  const { check, def, editedKeys, followCandidateKeys } = context
  const stored = new Map(check.fields.map((field) => [field.key, field]))
  const orNull = (text: string) => (text.trim() === '' ? null : text.trim())

  const fields = def.fields.map((field) => {
    const value = values.fields[field.key]!
    if (field.sensitive) {
      if (value.clear) {
        return { key: field.key, clear: true, verifiedTick: value.verifiedTick }
      }
      const replacement = value.replacement.trim()
      return replacement
        ? { key: field.key, value: replacement, verifiedTick: value.verifiedTick }
        : { key: field.key, verifiedTick: value.verifiedTick }
    }
    if (field.prefill) {
      if (followCandidateKeys.has(field.key)) {
        return { key: field.key, manual: false, verifiedTick: value.verifiedTick }
      }
      const manual = editedKeys.has(field.key) || stored.get(field.key)?.manual === true
      return manual
        ? { key: field.key, value: value.value, manual: true, verifiedTick: value.verifiedTick }
        : { key: field.key, manual: false, verifiedTick: value.verifiedTick }
    }
    return { key: field.key, value: value.value, verifiedTick: value.verifiedTick }
  })

  return {
    version: check.version,
    title: values.title.trim(),
    summaryDescription: orNull(values.summaryDescription),
    thisCardVerifies: orNull(values.thisCardVerifies),
    status: values.status,
    verificationType: values.verificationType.trim(),
    requestedDate: values.requestedDate || null,
    completedDate: values.completedDate || null,
    remarks: orNull(values.remarks),
    hasAttestation: values.hasAttestation,
    barCouncilNo: orNull(values.barCouncilNo),
    disclaimer: orNull(values.disclaimer),
    commentsOnNextPage: values.commentsOnNextPage,
    fields,
    details: values.details
      .filter((row) => row.label.trim() !== '' || row.value.trim() !== '')
      .map((row) => ({ label: row.label.trim(), value: orNull(row.value) })),
  }
}
