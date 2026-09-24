import { normalizeIndianPhone } from '../schemas'
import type { FieldKind } from './types'

/**
 * The value rules of the backend (`FieldValues`), mirrored so people see a problem next to the field
 * before saving. Blank is always fine (a draft). The server checks everything again.
 */

// Verhoeff checksum tables (the last digit of an Aadhaar number is a check digit).
const D = [
  [0, 1, 2, 3, 4, 5, 6, 7, 8, 9],
  [1, 2, 3, 4, 0, 6, 7, 8, 9, 5],
  [2, 3, 4, 0, 1, 7, 8, 9, 5, 6],
  [3, 4, 0, 1, 2, 8, 9, 5, 6, 7],
  [4, 0, 1, 2, 3, 9, 5, 6, 7, 8],
  [5, 9, 8, 7, 6, 0, 4, 3, 2, 1],
  [6, 5, 9, 8, 7, 1, 0, 4, 3, 2],
  [7, 6, 5, 9, 8, 2, 1, 0, 4, 3],
  [8, 7, 6, 5, 9, 3, 2, 1, 0, 4],
  [9, 8, 7, 6, 5, 4, 3, 2, 1, 0],
]
const P = [
  [0, 1, 2, 3, 4, 5, 6, 7, 8, 9],
  [1, 5, 7, 6, 2, 8, 3, 0, 9, 4],
  [5, 8, 0, 3, 7, 9, 6, 1, 4, 2],
  [8, 9, 1, 6, 0, 4, 3, 5, 2, 7],
  [9, 4, 5, 3, 1, 2, 6, 8, 7, 0],
  [4, 2, 8, 6, 5, 7, 3, 9, 0, 1],
  [2, 7, 9, 3, 8, 0, 6, 4, 1, 5],
  [7, 0, 4, 6, 9, 1, 3, 2, 5, 8],
]

export function verhoeffValid(digits: string): boolean {
  if (!/^\d+$/.test(digits)) {
    return false
  }
  let c = 0
  const reversed = digits.split('').reverse()
  reversed.forEach((char, index) => {
    c = D[c]![P[index % 8]![Number(char)]!]!
  })
  return c === 0
}

const MAX_TEXT = 500
const MAX_TEXTAREA = 5000
const MAX_ROWS = 50

/** A short reason the value is not acceptable for its kind, or null when it is fine (or blank). */
export function validateScalar(kind: FieldKind, raw: string, options: string[] = []): string | null {
  const value = raw.trim()
  if (value === '') {
    return null
  }
  switch (kind) {
    case 'text':
      return value.length > MAX_TEXT ? `is too long (at most ${MAX_TEXT} characters)` : null
    case 'textarea':
      return value.length > MAX_TEXTAREA ? `is too long (at most ${MAX_TEXTAREA} characters)` : null
    case 'date': {
      if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || Number.isNaN(Date.parse(value))) {
        return 'must be a date'
      }
      return value < '1900-01-01' || value > '2100-12-31' ? 'must be a date between 1900 and 2100' : null
    }
    case 'number':
      return /^-?\d{1,12}(\.\d{1,6})?$/.test(value) ? null : 'must be a number'
    case 'pin':
      return /^[1-9]\d{5}$/.test(value) ? null : 'must be a 6-digit PIN code'
    case 'phone':
      return normalizeIndianPhone(value) ? null : 'must be a valid Indian mobile number'
    case 'aadhaar': {
      const digits = value.replace(/[\s-]/g, '')
      return /^[2-9]\d{11}$/.test(digits) && verhoeffValid(digits) ? null : 'is not a valid Aadhaar number'
    }
    case 'pan':
      return /^[A-Z]{5}\d{4}[A-Z]$/.test(value.replace(/\s/g, '').toUpperCase())
        ? null
        : 'is not a valid PAN (five letters, four digits, one letter)'
    case 'uan':
      return /^\d{12}$/.test(value.replace(/[\s-]/g, '')) ? null : 'must be a 12-digit UAN'
    case 'boolean':
      return ['true', 'false', 'yes', 'no'].includes(value.toLowerCase()) ? null : 'must be yes or no'
    case 'select':
      return options.includes(value) ? null : 'must be one of the listed choices'
    case 'repeatable':
      return null // rows are checked cell by cell below
  }
}

export interface RepeatableRow {
  [column: string]: string
}

/** Rows of a repeatable field are stored as a JSON array of objects. */
export function parseRows(stored: string | null | undefined): RepeatableRow[] {
  if (!stored) {
    return []
  }
  try {
    const parsed: unknown = JSON.parse(stored)
    return Array.isArray(parsed) ? (parsed as RepeatableRow[]) : []
  } catch {
    return []
  }
}

export function stringifyRows(rows: RepeatableRow[]): string {
  const filled = rows.filter((row) => Object.values(row).some((cell) => cell.trim() !== ''))
  return filled.length === 0 ? '' : JSON.stringify(filled)
}

/** Checks each cell of a repeatable field by its column's kind. */
export function validateRows(
  stored: string,
  columns: { key: string; label: string; type: FieldKind }[],
): string | null {
  const rows = parseRows(stored)
  if (rows.length > MAX_ROWS) {
    return `has too many rows (at most ${MAX_ROWS})`
  }
  for (const row of rows) {
    for (const column of columns) {
      const problem = validateScalar(column.type, row[column.key] ?? '')
      if (problem) {
        return `${column.label} ${problem}`
      }
    }
  }
  return null
}

export function validateField(
  def: { type: FieldKind; options: string[]; itemFields: { key: string; label: string; type: FieldKind }[] },
  raw: string,
): string | null {
  return def.type === 'repeatable' ? validateRows(raw, def.itemFields) : validateScalar(def.type, raw, def.options)
}
