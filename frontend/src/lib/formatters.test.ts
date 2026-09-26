import { describe, expect, it } from 'vitest'
import { formatAadhaar, formatPan, formatPhone, formatPin, formatUan, upperCase } from './formatters'

describe('formatters', () => {
  it('puts names in capitals', () => {
    expect(upperCase('Asha Rao')).toBe('ASHA RAO')
  })

  it('keeps a PIN to six digits', () => {
    expect(formatPin('560 038')).toBe('560038')
    expect(formatPin('5600389')).toBe('560038')
    expect(formatPin('ab12')).toBe('12')
  })

  it('groups an Aadhaar number in fours while it is typed', () => {
    expect(formatAadhaar('2345')).toBe('2345')
    expect(formatAadhaar('23456')).toBe('2345 6')
    expect(formatAadhaar('234567890124')).toBe('2345 6789 0124')
    expect(formatAadhaar('2345-6789-0124-99')).toBe('2345 6789 0124')
    expect(formatAadhaar('')).toBe('')
  })

  it('puts a PAN in capitals and drops spaces and symbols', () => {
    expect(formatPan('abcde 1234f')).toBe('ABCDE1234F')
    expect(formatPan('abcde1234fgh')).toBe('ABCDE1234F')
  })

  it('keeps a UAN to twelve digits', () => {
    expect(formatUan('1234 5678 9012 3')).toBe('123456789012')
  })

  describe('formatPhone', () => {
    it('adds +91 and groups five and five as digits are typed', () => {
      expect(formatPhone('9')).toBe('+91 9')
      expect(formatPhone('98765')).toBe('+91 98765')
      expect(formatPhone('987654')).toBe('+91 98765 4')
      expect(formatPhone('9876543210')).toBe('+91 98765 43210')
    })

    it('leaves the box empty when there are no digits, so the prefix can be deleted', () => {
      expect(formatPhone('')).toBe('')
      expect(formatPhone('+91 ')).toBe('')
      expect(formatPhone('+')).toBe('')
    })

    it('keeps editing its own output stable', () => {
      expect(formatPhone('+91 98765 43210')).toBe('+91 98765 43210')
      expect(formatPhone('+91 98765 ')).toBe('+91 98765')
      expect(formatPhone('+91 9876')).toBe('+91 9876')
    })

    it('accepts a number pasted with the country part or a leading zero', () => {
      expect(formatPhone('+919876543210')).toBe('+91 98765 43210')
      expect(formatPhone('919876543210')).toBe('+91 98765 43210')
      expect(formatPhone('09876543210')).toBe('+91 98765 43210')
      expect(formatPhone('00919876543210')).toBe('+91 98765 43210')
    })

    it('does not mistake a mobile number that starts with 91 for a country code', () => {
      expect(formatPhone('9123456789')).toBe('+91 91234 56789')
    })

    it('keeps ten digits at most', () => {
      expect(formatPhone('98765432101234')).toBe('+91 98765 43210')
    })
  })
})
