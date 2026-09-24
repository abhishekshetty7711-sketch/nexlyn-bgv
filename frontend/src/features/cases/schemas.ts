import { z } from 'zod'

/**
 * The rules of the backend, mirrored so people get feedback before they save. The server checks
 * everything again and is always the final judge.
 */

/** Same reduction as the backend: spaces, dashes and brackets removed, +91 / 91 / 0 prefix accepted. */
export function normalizeIndianPhone(input: string): string | null {
  let digits = input.replace(/[\s\-().]/g, '')
  if (digits.startsWith('+91')) {
    digits = digits.slice(3)
  } else if (digits.startsWith('0091')) {
    digits = digits.slice(4)
  } else if (digits.startsWith('91') && digits.length === 12) {
    digits = digits.slice(2)
  } else if (digits.startsWith('0') && digits.length === 11) {
    digits = digits.slice(1)
  }
  return /^[6-9][0-9]{9}$/.test(digits) ? `+91${digits}` : null
}

export const REPORT_ID_PATTERN = /^[A-Za-z0-9][A-Za-z0-9/_-]{2,29}$/

const optionalText = (max: number, message = 'That is too long') => z.string().trim().max(max, message)

export const reportInfoSchema = z.object({
  reportId: z
    .string()
    .trim()
    .regex(REPORT_ID_PATTERN, 'Use 3-30 letters, digits, dashes, slashes or underscores'),
  issueDate: z.string().min(1, 'Choose the issue date'),
  clientId: z.string().min(1, 'Choose a client'),
  companyDisplayName: optionalText(1000),
  dueDate: z.string(),
})
export type ReportInfoValues = z.infer<typeof reportInfoSchema>

export const candidateSchema = z.object({
  fullName: optionalText(200),
  parentType: z.enum(['FATHER', 'GUARDIAN']),
  parentName: optionalText(200),
  employeeId: optionalText(50),
  dob: z.string().refine((value) => value === '' || (value >= '1900-01-01' && value <= todayIso()), {
    message: 'Enter a date of birth between 1900 and today',
  }),
  phone: z.string().refine((value) => value.trim() === '' || normalizeIndianPhone(value) !== null, {
    message: 'Enter a valid Indian mobile number, for example 98765 43210',
  }),
  street: optionalText(300),
  city: optionalText(100),
  state: optionalText(100),
  pin: z.string().trim().refine((value) => value === '' || /^[1-9][0-9]{5}$/.test(value), {
    message: 'Enter a 6-digit PIN code',
  }),
  country: optionalText(100),
})
export type CandidateValues = z.infer<typeof candidateSchema>

export const periodSchema = z
  .object({ show: z.boolean(), start: z.string(), end: z.string() })
  .refine((values) => !values.start || !values.end || values.end >= values.start, {
    path: ['end'],
    message: 'The end date cannot be before the start date',
  })
export type PeriodValues = z.infer<typeof periodSchema>

const nonNegativeWhole = z
  .string()
  .refine((value) => value.trim() === '' || /^\d+$/.test(value.trim()), { message: 'Enter a whole number, or leave empty' })

export const overviewSchema = z.object({
  statusPreset: z.enum(['COMPLETED', 'DISCREPANCY', 'UNABLE', 'CLOSED']),
  statusTitle: optionalText(100),
  statusSubtitle: optionalText(200),
  totalOverride: nonNegativeWhole,
  completedOverride: nonNegativeWhole,
  overallStatusOverride: optionalText(100),
})
export type OverviewValues = z.infer<typeof overviewSchema>

export const remarksSchema = z.object({
  analystRemarks: z.string().max(20000, 'That is too long'),
  finalRecommendation: z.string().max(20000, 'That is too long'),
})
export type RemarksValues = z.infer<typeof remarksSchema>

export const settingsSchema = z
  .object({
    layoutCards: z.enum(['4', '6']),
    dateFormat: z.enum(['NUMERIC', 'TEXT']),
    watermarkEnabled: z.boolean(),
    watermarkText: z.string().trim().max(40, 'At most 40 characters'),
  })
  .refine((values) => !values.watermarkEnabled || values.watermarkText !== '', {
    path: ['watermarkText'],
    message: 'Enter the watermark text, or switch the watermark off',
  })
export type SettingsValues = z.infer<typeof settingsSchema>

export const newCaseSchema = z.object({
  clientId: z.string().min(1, 'Choose a client'),
  issueDate: z.string(),
  dueDate: z.string(),
})
export type NewCaseValues = z.infer<typeof newCaseSchema>

export const clientSchema = z.object({
  name: z.string().trim().min(1, 'Enter the client name').max(200, 'That name is too long'),
  displayName: z.string().trim().min(1, 'Enter the name as it should print on reports').max(1000, 'That is too long'),
  defaultCheckTypes: z.string().max(1000, 'That is too long'),
  active: z.boolean(),
})
export type ClientValues = z.infer<typeof clientSchema>

/** Today as yyyy-mm-dd in the browser's time zone (what a date input uses). */
export function todayIso(): string {
  const now = new Date()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}
