import { describe, expect, it } from 'vitest'
import { parseRows, stringifyRows, validateField, validateRows, validateScalar, verhoeffValid } from './validation'

// A number with a correct check digit (the same one the backend tests use: 23456789012 + its digit).
const VALID_AADHAAR = '234567890124'

describe('verhoeffValid', () => {
  it('accepts a correct number and the classic worked example', () => {
    expect(verhoeffValid('2363')).toBe(true)
    expect(verhoeffValid(VALID_AADHAAR)).toBe(true)
  })

  it('catches every single wrong digit and every swap of neighbours', () => {
    for (let i = 0; i < VALID_AADHAAR.length; i += 1) {
      for (let d = 0; d <= 9; d += 1) {
        if (String(d) !== VALID_AADHAAR[i]) {
          expect(verhoeffValid(VALID_AADHAAR.slice(0, i) + d + VALID_AADHAAR.slice(i + 1))).toBe(false)
        }
      }
    }
    for (let i = 0; i < VALID_AADHAAR.length - 1; i += 1) {
      if (VALID_AADHAAR[i] !== VALID_AADHAAR[i + 1]) {
        const swapped = VALID_AADHAAR.slice(0, i) + VALID_AADHAAR[i + 1] + VALID_AADHAAR[i] + VALID_AADHAAR.slice(i + 2)
        expect(verhoeffValid(swapped)).toBe(false)
      }
    }
  })

  it('rejects non-numbers', () => {
    expect(verhoeffValid('')).toBe(false)
    expect(verhoeffValid('23a3')).toBe(false)
  })
})

describe('validateScalar', () => {
  it('lets blank through for every kind (drafts are allowed)', () => {
    for (const kind of ['text', 'textarea', 'date', 'number', 'pin', 'phone', 'aadhaar', 'pan', 'uan', 'boolean', 'select', 'repeatable'] as const) {
      expect(validateScalar(kind, '   ')).toBeNull()
    }
  })

  it('checks text lengths', () => {
    expect(validateScalar('text', 'x'.repeat(500))).toBeNull()
    expect(validateScalar('text', 'x'.repeat(501))).toMatch(/too long/)
    expect(validateScalar('textarea', 'x'.repeat(5001))).toMatch(/too long/)
  })

  it('checks dates, numbers, PINs and phones', () => {
    expect(validateScalar('date', '2026-05-17')).toBeNull()
    expect(validateScalar('date', '17/05/2026')).toMatch(/date/)
    expect(validateScalar('date', '1850-01-01')).toMatch(/1900/)
    expect(validateScalar('number', '-3.5')).toBeNull()
    expect(validateScalar('number', '12a')).toMatch(/number/)
    expect(validateScalar('pin', '560001')).toBeNull()
    expect(validateScalar('pin', '060001')).toMatch(/PIN/)
    expect(validateScalar('phone', '98765 43210')).toBeNull()
    expect(validateScalar('phone', '12345')).toMatch(/mobile/)
  })

  it('checks identity numbers', () => {
    expect(validateScalar('aadhaar', VALID_AADHAAR)).toBeNull()
    expect(validateScalar('aadhaar', '2345 6789 0124')).toBeNull()
    expect(validateScalar('aadhaar', '234567890125')).toMatch(/Aadhaar/)
    expect(validateScalar('aadhaar', '034567890124')).toMatch(/Aadhaar/)
    expect(validateScalar('pan', 'abcde1234f')).toBeNull()
    expect(validateScalar('pan', 'ABCD12345F')).toMatch(/PAN/)
    expect(validateScalar('uan', '1012 3456 7890')).toBeNull()
    expect(validateScalar('uan', '12345')).toMatch(/12-digit/)
  })

  it('checks yes/no and choices', () => {
    expect(validateScalar('boolean', 'Yes')).toBeNull()
    expect(validateScalar('boolean', 'maybe')).toMatch(/yes or no/)
    expect(validateScalar('select', 'Postal', ['Field', 'Postal'])).toBeNull()
    expect(validateScalar('select', 'Courier', ['Field', 'Postal'])).toMatch(/choices/)
  })
})

describe('repeatable rows', () => {
  const columns = [
    { key: 'from', label: 'From', type: 'date' as const },
    { key: 'reason', label: 'Reason', type: 'text' as const },
  ]

  it('round-trips rows and drops empty ones', () => {
    expect(parseRows('[{"from":"2020-01-01","reason":"x"}]')).toEqual([{ from: '2020-01-01', reason: 'x' }])
    expect(stringifyRows([{ from: '2020-01-01', reason: 'x' }, { from: '', reason: ' ' }])).toBe('[{"from":"2020-01-01","reason":"x"}]')
    expect(stringifyRows([{ from: '', reason: '' }])).toBe('')
    expect(parseRows(null)).toEqual([])
    expect(parseRows('not json')).toEqual([])
    expect(parseRows('{"a":1}')).toEqual([])
  })

  it('checks each cell by its column kind', () => {
    expect(validateRows('[{"from":"2020-01-01","reason":"x"}]', columns)).toBeNull()
    expect(validateRows('[{"from":"yesterday","reason":"x"}]', columns)).toMatch(/From must be a date/)
    expect(validateRows(JSON.stringify(Array.from({ length: 51 }, () => ({ reason: 'x' }))), columns)).toMatch(/too many rows/)
  })

  it('validateField picks the right rule for the field', () => {
    expect(validateField({ type: 'repeatable', options: [], itemFields: columns }, '[{"from":"bad"}]')).toMatch(/From/)
    expect(validateField({ type: 'pin', options: [], itemFields: [] }, '12')).toMatch(/PIN/)
  })
})
