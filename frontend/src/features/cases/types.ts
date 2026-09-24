export type Lifecycle = 'DRAFT' | 'IN_REVIEW' | 'CHANGES_REQUESTED' | 'APPROVED' | 'FINALIZED'
export type ParentType = 'FATHER' | 'GUARDIAN'
export type StatusPreset = 'COMPLETED' | 'DISCREPANCY' | 'UNABLE' | 'CLOSED'
export type DateFormat = 'NUMERIC' | 'TEXT'
export type CaseRole = 'PREPARER' | 'REVIEWER'

export interface ClientRef {
  id: string
  name: string
  displayName: string
}

export interface CandidateView {
  fullName: string | null
  parentType: ParentType
  parentName: string | null
  employeeId: string | null
  dob: string | null
  phone: string | null
  phoneDisplay: string | null
  street: string | null
  city: string | null
  state: string | null
  pin: string | null
  country: string
  hasPhoto: boolean
  photoDocumentId: string | null
}

export interface Overview {
  total: number
  completed: number
  overallStatus: string
}

export interface OverviewView {
  statusPreset: StatusPreset
  statusTitle: string
  statusSubtitle: string
  totalOverride: number | null
  completedOverride: number | null
  overallStatusOverride: string | null
  auto: Overview
  effective: Overview
}

export interface AssignmentView {
  adminId: string
  fullName: string
  email: string | null
  role: CaseRole
  assignedAt: string
}

/** The whole workspace of one case. `version` must be sent back with every save. */
export interface CaseView {
  id: string
  reportId: string
  lifecycle: Lifecycle
  editable: boolean
  version: number
  issueDate: string
  dueDate: string | null
  reviewComment: string | null
  client: ClientRef
  companyDisplayName: string | null
  candidate: CandidateView
  period: { show: boolean; start: string | null; end: string | null }
  overview: OverviewView
  remarks: { analystRemarks: string | null; finalRecommendation: string | null }
  settings: { layoutCards: number; dateFormat: DateFormat; watermarkEnabled: boolean; watermarkText: string }
  assignments: AssignmentView[]
  savedSections: Record<string, string>
  createdAt: string
  updatedAt: string
}

export interface CaseRow {
  id: string
  reportId: string
  clientName: string
  candidateName: string | null
  employeeId: string | null
  lifecycle: Lifecycle
  issueDate: string
  dueDate: string | null
  assignments: AssignmentView[]
  savedSections: number
  updatedAt: string
}

export interface ValidationIssue {
  section: string
  field: string
  message: string
}

export interface ValidationResult {
  errors: ValidationIssue[]
  warnings: ValidationIssue[]
}

export type SectionState = 'NOT_STARTED' | 'SAVED' | 'WARNING'

export interface SectionProgress {
  key: string
  label: string
  state: SectionState
  issues: number
}

export interface Progress {
  percent: number
  sections: SectionProgress[]
  totalChecks: number
  checksByStatus: Record<string, number>
}

export interface AdminSummary {
  id: string
  email: string
  fullName: string
  active: boolean
}

export interface ClientView {
  id: string
  name: string
  displayName: string
  defaultCheckTypes: string[]
  active: boolean
  version: number
}

export const LIFECYCLE_LABELS: Record<Lifecycle, string> = {
  DRAFT: 'Draft',
  IN_REVIEW: 'In review',
  CHANGES_REQUESTED: 'Changes requested',
  APPROVED: 'Approved',
  FINALIZED: 'Finalized',
}

/** The sections of the workspace, in navigator order (CLAUDE.md section 7). */
export const SECTION_KEYS = [
  'report-info',
  'candidate',
  'verification-period',
  'checks',
  'overview',
  'remarks',
  'settings',
  'generate',
] as const
export type SectionKey = (typeof SECTION_KEYS)[number]

export function isSectionKey(value: string | null): value is SectionKey {
  return value !== null && (SECTION_KEYS as readonly string[]).includes(value)
}
