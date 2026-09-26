import type { CheckFieldView, CheckTypeDef, CheckView } from './types'

/** Definitions as `GET /api/check-types` returns them (a small selection: enough to test every field kind). */
export const aadhaarDef: CheckTypeDef = {
  code: 'AADHAAR',
  order: 1,
  displayName: 'Identity Verification (Aadhaar)',
  documentName: 'Aadhaar Card',
  iconGroup: 'identity',
  attestationDefault: false,
  verificationTypeDefault: 'Electronic',
  fields: [
    { key: 'aadhaar_number', label: 'Aadhaar Number', type: 'aadhaar', sensitive: true, required: true, prefill: null, labelByParentType: false, options: [], itemFields: [] },
    { key: 'full_name', label: 'Full Name', type: 'text', sensitive: false, required: false, prefill: 'fullName', labelByParentType: false, options: [], itemFields: [] },
    { key: 'dob', label: 'DOB', type: 'date', sensitive: false, required: false, prefill: 'dob', labelByParentType: false, options: [], itemFields: [] },
    { key: 'city', label: 'City / Town', type: 'text', sensitive: false, required: false, prefill: 'city', labelByParentType: false, options: [], itemFields: [] },
    { key: 'pin', label: 'PIN Code', type: 'pin', sensitive: false, required: false, prefill: 'pin', labelByParentType: false, options: [], itemFields: [] },
  ],
  details: [],
}

export const courtDef: CheckTypeDef = {
  code: 'COURT',
  order: 3,
  displayName: 'Court Record (Permanent Address)',
  documentName: 'Court Document',
  iconGroup: 'court',
  attestationDefault: true,
  verificationTypeDefault: 'Standard',
  fields: [
    { key: 'father_name', label: "Father's Name", type: 'text', sensitive: false, required: false, prefill: 'parentName', labelByParentType: true, options: [], itemFields: [] },
    { key: 'search_period', label: 'Search Period', type: 'text', sensitive: false, required: false, prefill: null, labelByParentType: false, options: [], itemFields: [] },
  ],
  details: [{ label: 'Court Type', defaultValue: '' }, { label: 'Jurisdiction', defaultValue: 'Permanent Address' }],
}

export const gapDef: CheckTypeDef = {
  code: 'GAP_REVIEW',
  order: 13,
  displayName: 'Gap Review',
  documentName: 'Employment Gap',
  iconGroup: 'unique',
  attestationDefault: false,
  verificationTypeDefault: 'Standard',
  fields: [
    {
      key: 'gaps', label: 'Gap Periods', type: 'repeatable', sensitive: false, required: false, prefill: null, labelByParentType: false, options: [],
      itemFields: [{ key: 'from', label: 'From', type: 'date' }, { key: 'to', label: 'To', type: 'date' }, { key: 'reason', label: 'Reason', type: 'text' }],
    },
    { key: 'result', label: 'Result', type: 'select', sensitive: false, required: false, prefill: null, labelByParentType: false, options: ['Clear', 'Adverse'], itemFields: [] },
    { key: 'hits', label: 'Hits Found', type: 'boolean', sensitive: false, required: false, prefill: null, labelByParentType: false, options: [], itemFields: [] },
  ],
  details: [],
}

export const allDefs = [aadhaarDef, courtDef, gapDef]

function field(def: CheckTypeDef, key: string, overrides: Partial<CheckFieldView> = {}): CheckFieldView {
  const d = def.fields.find((f) => f.key === key)!
  return {
    key: d.key,
    label: d.label,
    type: d.type,
    sensitive: d.sensitive,
    required: d.required,
    value: null,
    hasValue: false,
    verifiedTick: false,
    manual: false,
    source: d.prefill ? 'CANDIDATE' : 'MANUAL',
    ...overrides,
  }
}

/** A freshly added Aadhaar check whose candidate details have been filled in. */
export function checkFixture(overrides: Partial<CheckView> = {}): CheckView {
  return {
    id: 'ck-1',
    caseId: 'c-1',
    type: 'AADHAAR',
    displayName: aadhaarDef.displayName,
    documentName: aadhaarDef.documentName,
    groupKey: 'identity',
    title: aadhaarDef.displayName,
    summaryDescription: null,
    thisCardVerifies: null,
    status: 'PENDING',
    verificationType: 'Electronic',
    requestedDate: null,
    completedDate: null,
    dateSync: 'MASTER',
    remarks: null,
    hasAttestation: false,
    barCouncilNo: null,
    disclaimer: null,
    commentsOnNextPage: false,
    sortOrder: 0,
    version: 0,
    fields: [
      field(aadhaarDef, 'aadhaar_number'),
      field(aadhaarDef, 'full_name', { value: 'Asha Rao', hasValue: true }),
      field(aadhaarDef, 'dob', { value: '1994-05-17', hasValue: true }),
      field(aadhaarDef, 'city', { value: 'Bengaluru', hasValue: true }),
      field(aadhaarDef, 'pin', { value: '560001', hasValue: true }),
    ],
    details: [],
    freeSections: [],
    updatedAt: '2026-09-24T10:00:00Z',
    ...overrides,
  }
}

export function courtFixture(overrides: Partial<CheckView> = {}): CheckView {
  return {
    ...checkFixture(),
    id: 'ck-2',
    type: 'COURT',
    displayName: courtDef.displayName,
    documentName: courtDef.documentName,
    groupKey: 'court',
    title: courtDef.displayName,
    verificationType: 'Standard',
    dateSync: 'AUTO',
    sortOrder: 1,
    hasAttestation: true,
    barCouncilNo: 'KAR/670/06',
    disclaimer: 'This report is based on information available in accessible court records.',
    commentsOnNextPage: false,
    fields: [
      field(courtDef, 'father_name', { value: 'Ravi Rao', hasValue: true }),
      field(courtDef, 'search_period', {}),
    ],
    details: [{ label: 'Court Type', value: null }, { label: 'Jurisdiction', value: 'Permanent Address' }],
    ...overrides,
  }
}

export function gapFixture(overrides: Partial<CheckView> = {}): CheckView {
  return {
    ...checkFixture(),
    id: 'ck-3',
    type: 'GAP_REVIEW',
    displayName: gapDef.displayName,
    documentName: gapDef.documentName,
    groupKey: 'GAP_REVIEW',
    title: gapDef.displayName,
    verificationType: 'Standard',
    dateSync: 'AUTO',
    sortOrder: 2,
    fields: [field(gapDef, 'gaps'), field(gapDef, 'result'), field(gapDef, 'hits')],
    ...overrides,
  }
}
