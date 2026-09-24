import { describe, expect, it } from 'vitest'
import { formatDate, isStaleVersion } from './format'
import {
  candidateSchema,
  clientSchema,
  normalizeIndianPhone,
  overviewSchema,
  periodSchema,
  REPORT_ID_PATTERN,
  reportInfoSchema,
  settingsSchema,
} from './schemas'

describe('normalizeIndianPhone (mirror of the backend)', () => {
  it('reduces the usual ways of typing a number to one form', () => {
    for (const typed of ['9876543210', '98765 43210', '+91 98765 43210', '+91-98765-43210', '09876543210', '919876543210', '0091 98765 43210', '(+91) 98765.43210']) {
      expect(normalizeIndianPhone(typed), typed).toBe('+919876543210')
    }
  })

  it('rejects numbers that cannot be Indian mobiles', () => {
    for (const bad of ['', '12345', '5876543210', '98765432', '98765432101', 'abcdefghij', '+1 415 555 2671', '+91 12345 67890']) {
      expect(normalizeIndianPhone(bad), bad).toBeNull()
    }
  })
})

describe('report info', () => {
  it('accepts well-formed Report IDs and refuses the rest', () => {
    for (const good of ['NX-2026-0001', 'ACME/2026-77', 'abc', 'A_b-c/1']) {
      expect(REPORT_ID_PATTERN.test(good), good).toBe(true)
    }
    for (const bad of ['', 'ab', 'has space', 'semi;colon', 'x'.repeat(31), '-leading']) {
      expect(REPORT_ID_PATTERN.test(bad), bad).toBe(false)
    }
    const values = { reportId: 'ab', issueDate: '2026-01-01', clientId: 'c', companyDisplayName: '', dueDate: '' }
    expect(reportInfoSchema.safeParse(values).success).toBe(false)
    expect(reportInfoSchema.safeParse({ ...values, reportId: 'NX-2026-1' }).success).toBe(true)
    expect(reportInfoSchema.safeParse({ ...values, reportId: 'NX-2026-1', issueDate: '' }).success).toBe(false)
    expect(reportInfoSchema.safeParse({ ...values, reportId: 'NX-2026-1', clientId: '' }).success).toBe(false)
  })
})

describe('candidate', () => {
  const base = {
    fullName: '', parentType: 'FATHER' as const, parentName: '', employeeId: '', dob: '', phone: '',
    street: '', city: '', state: '', pin: '', country: 'India',
  }

  it('lets everything be empty (a draft), because the required fields are checked at validation time', () => {
    expect(candidateSchema.safeParse(base).success).toBe(true)
  })

  it('checks the phone, the PIN and the date of birth', () => {
    expect(candidateSchema.safeParse({ ...base, phone: '98765 43210' }).success).toBe(true)
    expect(candidateSchema.safeParse({ ...base, phone: '12345' }).success).toBe(false)
    expect(candidateSchema.safeParse({ ...base, pin: '560001' }).success).toBe(true)
    expect(candidateSchema.safeParse({ ...base, pin: '060001' }).success).toBe(false)
    expect(candidateSchema.safeParse({ ...base, pin: '5600' }).success).toBe(false)
    expect(candidateSchema.safeParse({ ...base, dob: '1994-05-17' }).success).toBe(true)
    expect(candidateSchema.safeParse({ ...base, dob: '1850-01-01' }).success).toBe(false)
    expect(candidateSchema.safeParse({ ...base, dob: '2999-01-01' }).success).toBe(false)
  })
})

describe('other sections', () => {
  it('needs the period to run forwards', () => {
    expect(periodSchema.safeParse({ show: true, start: '2026-05-10', end: '2026-05-01' }).success).toBe(false)
    expect(periodSchema.safeParse({ show: true, start: '2026-05-01', end: '2026-05-10' }).success).toBe(true)
    expect(periodSchema.safeParse({ show: false, start: '', end: '' }).success).toBe(true)
  })

  it('takes whole numbers or nothing for the overview overrides', () => {
    const base = { statusPreset: 'COMPLETED' as const, statusTitle: '', statusSubtitle: '', totalOverride: '', completedOverride: '', overallStatusOverride: '' }
    expect(overviewSchema.safeParse(base).success).toBe(true)
    expect(overviewSchema.safeParse({ ...base, totalOverride: '12' }).success).toBe(true)
    expect(overviewSchema.safeParse({ ...base, totalOverride: '-1' }).success).toBe(false)
    expect(overviewSchema.safeParse({ ...base, completedOverride: '1.5' }).success).toBe(false)
    expect(overviewSchema.safeParse({ ...base, totalOverride: 'many' }).success).toBe(false)
  })

  it('needs watermark text only when the watermark is on, and at most 40 characters', () => {
    const base = { layoutCards: '4' as const, dateFormat: 'NUMERIC' as const, watermarkEnabled: false, watermarkText: '' }
    expect(settingsSchema.safeParse(base).success).toBe(true)
    expect(settingsSchema.safeParse({ ...base, watermarkEnabled: true }).success).toBe(false)
    expect(settingsSchema.safeParse({ ...base, watermarkEnabled: true, watermarkText: 'CONFIDENTIAL' }).success).toBe(true)
    expect(settingsSchema.safeParse({ ...base, watermarkText: 'x'.repeat(41) }).success).toBe(false)
  })

  it('needs a client name and a printed name', () => {
    expect(clientSchema.safeParse({ name: 'Acme', displayName: 'Acme', defaultCheckTypes: [], active: true }).success).toBe(true)
    expect(clientSchema.safeParse({ name: '', displayName: 'Acme', defaultCheckTypes: [], active: true }).success).toBe(false)
    expect(clientSchema.safeParse({ name: 'Acme', displayName: ' ', defaultCheckTypes: [], active: true }).success).toBe(false)
  })
})

describe('formatDate', () => {
  it('prints the two report formats', () => {
    expect(formatDate('2026-06-11')).toBe('11/06/2026')
    expect(formatDate('2026-06-11', 'NUMERIC')).toBe('11/06/2026')
    expect(formatDate('2026-06-11', 'TEXT')).toBe('11-Jun-2026')
    expect(formatDate('2026-12-01T10:00:00Z', 'TEXT')).toBe('01-Dec-2026')
  })

  it('copes with nothing', () => {
    expect(formatDate(null)).toBe('')
    expect(formatDate(undefined)).toBe('')
  })
})

describe('isStaleVersion', () => {
  it('recognises a 409', () => {
    expect(isStaleVersion({ status: 409 })).toBe(true)
    expect(isStaleVersion({ status: 400 })).toBe(false)
    expect(isStaleVersion(new Error('x'))).toBe(false)
    expect(isStaleVersion(null)).toBe(false)
  })
})
