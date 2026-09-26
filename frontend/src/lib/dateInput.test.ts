import { describe, expect, it } from 'vitest'
import { displayToIso, isoToDisplay, isValidIsoDate, maskDate } from './dateInput'

describe('maskDate', () => {
  it('puts the slashes in while digits are typed', () => {
    expect(maskDate('1')).toBe('1')
    expect(maskDate('11')).toBe('11')
    expect(maskDate('110')).toBe('11/0')
    expect(maskDate('1106')).toBe('11/06')
    expect(maskDate('11062')).toBe('11/06/2')
    expect(maskDate('11062026')).toBe('11/06/2026')
  })

  it('drops anything that is not a digit and stops at eight digits', () => {
    expect(maskDate('11-06-2026')).toBe('11/06/2026')
    expect(maskDate('ab1c1')).toBe('11')
    expect(maskDate('110620261234')).toBe('11/06/2026')
    expect(maskDate('')).toBe('')
  })

  it('lets a slash be deleted by deleting the digit after it', () => {
    expect(maskDate('11/')).toBe('11')
    expect(maskDate('11/0')).toBe('11/0')
  })
})

describe('displayToIso and isoToDisplay', () => {
  it('turns a complete real date into an ISO date and back', () => {
    expect(displayToIso('11/06/2026')).toBe('2026-06-11')
    expect(isoToDisplay('2026-06-11')).toBe('11/06/2026')
  })

  it('refuses incomplete and impossible dates', () => {
    expect(displayToIso('11/06/202')).toBeNull()
    expect(displayToIso('31/02/2026')).toBeNull()
    expect(displayToIso('00/01/2026')).toBeNull()
    expect(displayToIso('11/13/2026')).toBeNull()
  })

  it('accepts a leap day only in a leap year', () => {
    expect(displayToIso('29/02/2024')).toBe('2024-02-29')
    expect(displayToIso('29/02/2026')).toBeNull()
  })

  it('leaves a half-typed value as it is', () => {
    expect(isoToDisplay('11/0')).toBe('11/0')
    expect(isoToDisplay('')).toBe('')
  })
})

describe('isValidIsoDate', () => {
  it('checks the calendar, not only the shape', () => {
    expect(isValidIsoDate('2026-06-11')).toBe(true)
    expect(isValidIsoDate('2026-02-31')).toBe(false)
    expect(isValidIsoDate('2026-6-11')).toBe(false)
    expect(isValidIsoDate('11/06/2026')).toBe(false)
  })
})
