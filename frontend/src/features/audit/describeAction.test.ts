import { describe, expect, it } from 'vitest'
import { describeAction } from './describeAction'

// Every action the backend publishes (AuditEvent action names, found by searching the modules).
const KNOWN_ACTIONS = [
  'LOGIN_SUCCESS', 'LOGOUT', 'TWO_FACTOR_ENABLED', 'TWO_FACTOR_FAILED', 'BACKUP_CODE_USED', 'ACCOUNT_LOCKED', 'PASSWORD_CHANGED',
  'PASSWORD_CHANGE_FAILED', 'ADMIN_BOOTSTRAPPED', 'ADMIN_INVITED', 'INVITATION_ACCEPTED', 'INVITATION_REVOKED', 'ADMIN_UPDATED',
  'ADMIN_DISABLED', 'ADMIN_ENABLED', 'ADMIN_UNLOCKED', 'SESSIONS_REVOKED', 'ROLE_CREATED', 'ROLE_UPDATED', 'ROLE_DELETED',
  'CLIENT_CREATED', 'CLIENT_UPDATED', 'CASE_CREATED', 'CASE_DELETED', 'CASE_ASSIGNED', 'CASE_UNASSIGNED', 'CHECK_ADDED', 'CHECK_SAVED',
  'CHECK_DELETED', 'CHECKS_REORDERED', 'ATTESTATION_CHANGED', 'PII_REVEALED', 'DOCUMENT_UPLOADED', 'DOCUMENT_UPDATED',
  'DOCUMENT_DELETED', 'DOCUMENTS_REORDERED', 'DOCUMENT_VIEWED', 'CASE_SUBMITTED_FOR_REVIEW', 'CASE_APPROVED', 'CASE_CHANGES_REQUESTED',
  'CASE_FINALIZED', 'CASE_REOPENED', 'REPORT_GENERATED', 'REPORT_GENERATION_FAILED', 'REPORT_DOWNLOADED', 'REPORT_FINALIZED',
]

describe('describeAction', () => {
  it('has a sentence for every action the backend records, and none of them is the code', () => {
    for (const code of KNOWN_ACTIONS) {
      const sentence = describeAction(code)
      expect(sentence, code).not.toMatch(/_/)
      expect(sentence, code).not.toBe(code)
      expect(sentence.length, code).toBeGreaterThan(8)
      expect(sentence.charAt(0), code).toBe(sentence.charAt(0).toUpperCase())
    }
  })

  it('reads well for a few common ones', () => {
    expect(describeAction('DOCUMENT_VIEWED')).toBe('Viewed a document')
    expect(describeAction('ADMIN_DISABLED')).toBe('Disabled an admin account')
    expect(describeAction('SESSIONS_REVOKED')).toBe('Ended all sessions of an admin')
    expect(describeAction('CASE_SUBMITTED_FOR_REVIEW')).toBe('Sent a case for review')
  })

  it('explains why a sign-in failed, from the part after the colon', () => {
    expect(describeAction('LOGIN_FAILED:BAD_PASSWORD')).toBe('Failed sign-in attempt (wrong password)')
    expect(describeAction('LOGIN_FAILED:UNKNOWN_EMAIL')).toBe('Failed sign-in attempt (unknown e-mail address)')
    expect(describeAction('LOGIN_FAILED:DISABLED')).toBe('Failed sign-in attempt (the account is disabled)')
    expect(describeAction('LOGIN_FAILED:SOMETHING_ELSE')).toBe('Failed sign-in attempt (something else)')
  })

  it('names the section of a case that was saved', () => {
    expect(describeAction('CASE_SECTION_SAVED:report-info')).toBe('Saved the report info section of a case')
    expect(describeAction('CASE_SECTION_SAVED:verification-period')).toBe('Saved the verification period section of a case')
    expect(describeAction('CASE_SECTION_SAVED:candidate')).toBe('Saved the candidate details section of a case')
    expect(describeAction('CASE_SECTION_SAVED:new-section')).toBe('Saved a section of a case (new-section)') // unknown section: still readable
  })

  it('makes an action it does not know readable instead of blank', () => {
    expect(describeAction('SOME_NEW_ACTION')).toBe('Some new action')
    expect(describeAction('')).toBe('Unknown action')
  })
})
