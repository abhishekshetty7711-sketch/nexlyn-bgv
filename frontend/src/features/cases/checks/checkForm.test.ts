import { describe, expect, it } from 'vitest'
import { buildCheckSchema, toFormValues, toSaveInput } from './checkForm'
import { aadhaarDef, checkFixture } from './testFixtures'

const NONE: ReadonlySet<string> = new Set()

describe('toFormValues', () => {
  it('puts stored values in the form but never a sensitive number', () => {
    const values = toFormValues(checkFixture())
    expect(values.fields.full_name!.value).toBe('Asha Rao')
    expect(values.fields.aadhaar_number!.value).toBe('')
    expect(values.fields.aadhaar_number!.replacement).toBe('')
    expect(values.status).toBe('PENDING')
    expect(values.requestedDate).toBe('')
  })
})

describe('buildCheckSchema', () => {
  const schema = buildCheckSchema(aadhaarDef)

  it('accepts the values of a fresh check', () => {
    expect(schema.safeParse(toFormValues(checkFixture())).success).toBe(true)
  })

  it('needs a title', () => {
    const values = { ...toFormValues(checkFixture()), title: '  ' }
    const result = schema.safeParse(values)
    expect(result.success).toBe(false)
    expect(result.success ? [] : result.error.issues.map((issue) => issue.path.join('.'))).toContain('title')
  })

  it('checks a typed replacement Aadhaar number by the real checksum', () => {
    const values = toFormValues(checkFixture())
    values.fields.aadhaar_number!.replacement = '234567890125'
    const bad = schema.safeParse(values)
    expect(bad.success ? [] : bad.error.issues.map((issue) => issue.path.join('.'))).toContain('fields.aadhaar_number.replacement')
    values.fields.aadhaar_number!.replacement = '2345 6789 0124'
    expect(schema.safeParse(values).success).toBe(true)
  })

  it('checks ordinary fields by their kind', () => {
    const values = toFormValues(checkFixture())
    values.fields.pin!.value = '12'
    const bad = schema.safeParse(values)
    expect(bad.success ? [] : bad.error.issues.map((issue) => issue.path.join('.'))).toContain('fields.pin.value')
  })
})

describe('toSaveInput', () => {
  const check = checkFixture()

  it('sends a sensitive field only when a replacement is typed, or when it is cleared', () => {
    const values = toFormValues(check)
    const untouched = toSaveInput(values, { check, def: aadhaarDef, editedKeys: NONE, followCandidateKeys: NONE })
    expect(untouched.fields.find((f) => f.key === 'aadhaar_number')).toEqual({ key: 'aadhaar_number', verifiedTick: false })

    values.fields.aadhaar_number!.replacement = ' 234567890124 '
    const replaced = toSaveInput(values, { check, def: aadhaarDef, editedKeys: NONE, followCandidateKeys: NONE })
    expect(replaced.fields.find((f) => f.key === 'aadhaar_number')).toEqual({ key: 'aadhaar_number', value: '234567890124', verifiedTick: false })

    values.fields.aadhaar_number!.clear = true
    const cleared = toSaveInput(values, { check, def: aadhaarDef, editedKeys: NONE, followCandidateKeys: NONE })
    expect(cleared.fields.find((f) => f.key === 'aadhaar_number')).toEqual({ key: 'aadhaar_number', clear: true, verifiedTick: false })
  })

  it('lets a prefilled field keep following the candidate unless it was edited', () => {
    const values = toFormValues(check)
    const untouched = toSaveInput(values, { check, def: aadhaarDef, editedKeys: NONE, followCandidateKeys: NONE })
    expect(untouched.fields.find((f) => f.key === 'full_name')).toEqual({ key: 'full_name', manual: false, verifiedTick: false })

    values.fields.full_name!.value = 'Typed By Hand'
    const edited = toSaveInput(values, { check, def: aadhaarDef, editedKeys: new Set(['full_name']), followCandidateKeys: NONE })
    expect(edited.fields.find((f) => f.key === 'full_name')).toEqual({ key: 'full_name', value: 'Typed By Hand', manual: true, verifiedTick: false })
  })

  it('keeps a field manual that was already manual, and follows the candidate again on request', () => {
    const manualCheck = checkFixture({
      fields: checkFixture().fields.map((f) => (f.key === 'city' ? { ...f, manual: true, source: 'MANUAL' as const, value: 'Mysuru' } : f)),
    })
    const values = toFormValues(manualCheck)
    const kept = toSaveInput(values, { check: manualCheck, def: aadhaarDef, editedKeys: NONE, followCandidateKeys: NONE })
    expect(kept.fields.find((f) => f.key === 'city')).toEqual({ key: 'city', value: 'Mysuru', manual: true, verifiedTick: false })

    const followed = toSaveInput(values, { check: manualCheck, def: aadhaarDef, editedKeys: NONE, followCandidateKeys: new Set(['city']) })
    expect(followed.fields.find((f) => f.key === 'city')).toEqual({ key: 'city', manual: false, verifiedTick: false })
  })

  it('cleans the card values and drops empty detail rows', () => {
    const values = toFormValues(check)
    values.title = '  My title '
    values.summaryDescription = '   '
    values.requestedDate = '2026-03-01'
    values.details = [{ label: ' Court Type ', value: ' High Court ' }, { label: '', value: '' }, { label: 'Note', value: '' }]
    const input = toSaveInput(values, { check, def: aadhaarDef, editedKeys: NONE, followCandidateKeys: NONE })

    expect(input.version).toBe(check.version)
    expect(input.title).toBe('My title')
    expect(input.summaryDescription).toBeNull()
    expect(input.requestedDate).toBe('2026-03-01')
    expect(input.completedDate).toBeNull()
    expect(input.details).toEqual([{ label: 'Court Type', value: 'High Court' }, { label: 'Note', value: null }])
  })
})
