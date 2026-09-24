/** The kinds of field a check type can have (from the YAML definitions on the server). */
export type FieldKind =
  | 'text'
  | 'textarea'
  | 'date'
  | 'number'
  | 'pin'
  | 'phone'
  | 'aadhaar'
  | 'pan'
  | 'uan'
  | 'boolean'
  | 'select'
  | 'repeatable'

export interface ItemFieldDef {
  key: string
  label: string
  type: FieldKind
}

export interface FieldDef {
  key: string
  label: string
  type: FieldKind
  sensitive: boolean
  required: boolean
  /** Candidate property this field follows, or null. */
  prefill: string | null
  labelByParentType: boolean
  options: string[]
  itemFields: ItemFieldDef[]
}

/** One kind of verification; the form for it is drawn from this. */
export interface CheckTypeDef {
  code: string
  order: number
  displayName: string
  documentName: string
  iconGroup: string
  attestationDefault: boolean
  verificationTypeDefault: string
  fields: FieldDef[]
  details: { label: string; defaultValue: string }[]
}

export type CheckStatus = 'VERIFIED' | 'DISCREPANCY' | 'UNABLE_TO_VERIFY' | 'CLOSED' | 'PENDING' | 'IN_PROGRESS'

export const STATUS_ORDER: CheckStatus[] = ['PENDING', 'IN_PROGRESS', 'VERIFIED', 'DISCREPANCY', 'UNABLE_TO_VERIFY', 'CLOSED']

export const STATUS_LABELS: Record<CheckStatus, string> = {
  VERIFIED: 'Verified',
  DISCREPANCY: 'Discrepancy',
  UNABLE_TO_VERIFY: 'Unable to Verify',
  CLOSED: 'Closed',
  PENDING: 'Pending',
  IN_PROGRESS: 'In Progress',
}

/** The little mark shown with each status (CLAUDE.md section 6.2). */
export const STATUS_MARKS: Record<CheckStatus, string> = {
  VERIFIED: '✓',
  DISCREPANCY: '✕',
  UNABLE_TO_VERIFY: 'ⓘ',
  CLOSED: '−',
  PENDING: '⏱',
  IN_PROGRESS: '↻',
}

export type DateSync = 'MASTER' | 'AUTO' | 'MANUAL'
export type FieldSource = 'CANDIDATE' | 'MANUAL' | 'API'

/** `value` is the masked form for a sensitive field; `hasValue` says whether one is stored. */
export interface CheckFieldView {
  key: string
  label: string
  type: FieldKind
  sensitive: boolean
  required: boolean
  value: string | null
  hasValue: boolean
  verifiedTick: boolean
  manual: boolean
  source: FieldSource
}

export interface CheckFreeSectionView {
  id: string
  kind: 'TEXT' | 'IMAGE'
  text: string | null
  documentId: string | null
  sortOrder: number
}

export interface CheckView {
  id: string
  caseId: string
  type: string
  displayName: string
  documentName: string
  groupKey: string
  title: string
  summaryDescription: string | null
  thisCardVerifies: string | null
  status: CheckStatus
  verificationType: string
  requestedDate: string | null
  completedDate: string | null
  dateSync: DateSync
  remarks: string | null
  hasAttestation: boolean
  barCouncilNo: string | null
  disclaimer: string | null
  sortOrder: number
  version: number
  fields: CheckFieldView[]
  details: { label: string; value: string | null }[]
  freeSections: CheckFreeSectionView[]
  updatedAt: string
}
