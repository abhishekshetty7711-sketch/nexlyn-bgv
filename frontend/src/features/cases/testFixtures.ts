import type { CaseView, Progress, ValidationResult } from './types'

/** A freshly created case, as the API returns it. Override any part in a test. */
export function caseFixture(overrides: Partial<CaseView> = {}): CaseView {
  return {
    id: 'c-1',
    reportId: 'NX-2026-0001',
    lifecycle: 'DRAFT',
    editable: true,
    version: 0,
    issueDate: '2026-09-24',
    dueDate: null,
    reviewComment: null,
    client: { id: 'cl-1', name: 'Acme Corp', displayName: 'Acme Corp\nPrivate Limited' },
    companyDisplayName: null,
    candidate: {
      fullName: null,
      parentType: 'FATHER',
      parentName: null,
      employeeId: null,
      dob: null,
      phone: null,
      phoneDisplay: null,
      street: null,
      city: null,
      state: null,
      pin: null,
      country: 'India',
      hasPhoto: false,
      photoDocumentId: null,
    },
    period: { show: true, start: null, end: null },
    overview: {
      statusPreset: 'COMPLETED',
      statusTitle: 'Completed',
      statusSubtitle: 'All Requested Verifications Completed',
      totalOverride: null,
      completedOverride: null,
      overallStatusOverride: null,
      auto: { total: 0, completed: 0, overallStatus: 'Pending' },
      effective: { total: 0, completed: 0, overallStatus: 'Pending' },
    },
    remarks: { analystRemarks: null, finalRecommendation: null },
    settings: { layoutCards: 4, dateFormat: 'NUMERIC', watermarkEnabled: false, watermarkText: 'NEXLYN VERIFIED' },
    assignments: [{ adminId: 'a-1', fullName: 'Ann Analyst', email: 'a@example.com', role: 'PREPARER', assignedAt: '2026-09-24T10:00:00Z' }],
    savedSections: { 'report-info': '2026-09-24T10:00:00Z' },
    createdAt: '2026-09-24T10:00:00Z',
    updatedAt: '2026-09-24T10:00:00Z',
    workflow: {
      submittedAt: null,
      submittedByName: null,
      reviewedAt: null,
      reviewedByName: null,
      approvedAt: null,
      finalizedAt: null,
      finalizedByName: null,
      actions: { canSubmit: false, canApprove: false, canRequestChanges: false, canFinalize: false, canReopen: false },
    },
    ...overrides,
  }
}

export function progressFixture(overrides: Partial<Progress> = {}): Progress {
  return {
    percent: 14,
    sections: [
      { key: 'report-info', label: 'Report Info', state: 'SAVED', issues: 0 },
      { key: 'candidate', label: 'Candidate Details', state: 'NOT_STARTED', issues: 6 },
      { key: 'verification-period', label: 'Verification Period', state: 'NOT_STARTED', issues: 2 },
      { key: 'checks', label: 'Checks', state: 'NOT_STARTED', issues: 1 },
      { key: 'overview', label: 'Overview & Status', state: 'NOT_STARTED', issues: 0 },
      { key: 'remarks', label: 'Remarks & Recommendation', state: 'NOT_STARTED', issues: 2 },
      { key: 'settings', label: 'Report Settings', state: 'NOT_STARTED', issues: 0 },
      { key: 'generate', label: 'Generate Report', state: 'NOT_STARTED', issues: 0 },
    ],
    totalChecks: 0,
    checksByStatus: { VERIFIED: 0, DISCREPANCY: 0, UNABLE_TO_VERIFY: 0, CLOSED: 0, PENDING: 0, IN_PROGRESS: 0 },
    ...overrides,
  }
}

export function validationFixture(): ValidationResult {
  return {
    errors: [
      { section: 'candidate', field: 'fullName', message: "Candidate's full name is required." },
      { section: 'checks', field: 'checks', message: 'Add at least one verification check.' },
    ],
    warnings: [{ section: 'remarks', field: 'analystRemarks', message: 'Analyst remarks are empty.' }],
  }
}
