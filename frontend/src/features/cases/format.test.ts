import { describe, expect, it } from 'vitest'
import { formatDate, formatDateTime } from './format'

describe('formatDate', () => {
  it('writes an ISO date day first, in the two report styles', () => {
    expect(formatDate('2026-06-11')).toBe('11/06/2026')
    expect(formatDate('2026-06-11', 'TEXT')).toBe('11-Jun-2026')
  })

  it('is empty for nothing', () => {
    expect(formatDate(null)).toBe('')
    expect(formatDate(undefined)).toBe('')
    expect(formatDate('')).toBe('')
  })
})

describe('formatDateTime', () => {
  it('writes day first with a 24-hour clock, in the browser time zone', () => {
    // built from local parts, so the answer does not depend on the time zone of the machine running the test
    expect(formatDateTime(new Date(2026, 8, 25, 16, 53).toISOString())).toBe('25/09/2026, 16:53')
    expect(formatDateTime(new Date(2026, 0, 5, 0, 7).toISOString())).toBe('05/01/2026, 00:07')
    expect(formatDateTime(new Date(2026, 11, 31, 23, 59).toISOString())).toBe('31/12/2026, 23:59')
  })

  it('never writes month first or uses am / pm', () => {
    const text = formatDateTime(new Date(2026, 8, 3, 13, 5).toISOString())
    expect(text).toBe('03/09/2026, 13:05')
    expect(text).not.toMatch(/[ap]m/i)
  })

  it('is empty for nothing and gives an unreadable value back as it came', () => {
    expect(formatDateTime(null)).toBe('')
    expect(formatDateTime(undefined)).toBe('')
    expect(formatDateTime('')).toBe('')
    expect(formatDateTime('not a date')).toBe('not a date')
  })
})
