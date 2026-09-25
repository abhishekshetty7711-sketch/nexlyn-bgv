/**
 * The audit log stores action codes (`ADMIN_DISABLED`, `LOGIN_FAILED:BAD_PASSWORD`). This turns them into a short sentence about
 * what happened, without the person: the "Who" column already says that. Wording only; the stored entry is never changed, and the
 * code is still shown in the entry's details. A code this table does not know becomes readable words, never a blank.
 */

const ACTIONS: Record<string, string> = {
  // signing in and accounts
  LOGIN_SUCCESS: 'Signed in',
  LOGOUT: 'Signed out',
  TWO_FACTOR_ENABLED: 'Set up two-step sign-in',
  TWO_FACTOR_FAILED: 'Entered a wrong two-step code',
  BACKUP_CODE_USED: 'Used a backup code to sign in',
  ACCOUNT_LOCKED: 'Account locked after too many failed sign-ins',
  PASSWORD_CHANGED: 'Changed their password',
  PASSWORD_CHANGE_FAILED: 'Tried to change their password and did not succeed',
  // admins, invitations, roles
  ADMIN_BOOTSTRAPPED: 'First administrator account created',
  ADMIN_INVITED: 'Invited an admin',
  INVITATION_ACCEPTED: 'Accepted an invitation',
  INVITATION_REVOKED: 'Cancelled an invitation',
  ADMIN_UPDATED: "Changed an admin's details or roles",
  ADMIN_DISABLED: 'Disabled an admin account',
  ADMIN_ENABLED: 'Enabled an admin account',
  ADMIN_UNLOCKED: 'Unlocked an admin account',
  SESSIONS_REVOKED: 'Ended all sessions of an admin',
  ROLE_CREATED: 'Created a role',
  ROLE_UPDATED: 'Changed a role',
  ROLE_DELETED: 'Deleted a role',
  // clients and cases
  CLIENT_CREATED: 'Added a client',
  CLIENT_UPDATED: 'Changed a client',
  CASE_CREATED: 'Created a case',
  CASE_DELETED: 'Deleted a case',
  CASE_ASSIGNED: 'Assigned someone to a case',
  CASE_UNASSIGNED: 'Removed someone from a case',
  CHECK_ADDED: 'Added a check',
  CHECK_SAVED: 'Saved a check',
  CHECK_DELETED: 'Removed a check',
  CHECKS_REORDERED: 'Changed the order of the checks',
  ATTESTATION_CHANGED: "Turned the advocate's attestation on or off",
  PII_REVEALED: 'Revealed a full Aadhaar, PAN or similar number',
  // documents
  DOCUMENT_UPLOADED: 'Uploaded a document',
  DOCUMENT_UPDATED: "Changed a document's settings",
  DOCUMENT_DELETED: 'Deleted a document',
  DOCUMENTS_REORDERED: 'Changed the order of the documents',
  DOCUMENT_VIEWED: 'Viewed a document',
  // review and reports
  CASE_SUBMITTED_FOR_REVIEW: 'Sent a case for review',
  CASE_APPROVED: 'Approved a case',
  CASE_CHANGES_REQUESTED: 'Asked for changes on a case',
  CASE_FINALIZED: 'Finalized a case',
  CASE_REOPENED: 'Reopened a finalized case',
  REPORT_GENERATED: 'Generated a draft report',
  REPORT_GENERATION_FAILED: 'A report could not be generated',
  REPORT_DOWNLOADED: 'Downloaded a report',
  REPORT_FINALIZED: 'Finalized the report',
}

/** The parts after a colon in `CASE_SECTION_SAVED:report-info` (the section keys of the case workspace). */
const SECTIONS: Record<string, string> = {
  'report-info': 'report info',
  candidate: 'candidate details',
  'verification-period': 'verification period',
  overview: 'overview and status',
  remarks: 'remarks and recommendation',
  settings: 'report settings',
}

/** The reasons after a colon in `LOGIN_FAILED:BAD_PASSWORD`. */
const LOGIN_FAILURES: Record<string, string> = {
  BAD_PASSWORD: 'wrong password',
  UNKNOWN_EMAIL: 'unknown e-mail address',
  DISABLED: 'the account is disabled',
  LOCKED: 'the account is locked',
}

/** `SOME_NEW_ACTION` to `Some new action`: readable, and never blank. */
function humanize(code: string): string {
  const words = code.toLowerCase().replace(/[_:]+/g, ' ').trim()
  return words ? words.charAt(0).toUpperCase() + words.slice(1) : 'Unknown action'
}

/** A short sentence for an action code, without the person who did it. */
export function describeAction(action: string): string {
  const known = ACTIONS[action]
  if (known) {
    return known
  }
  const [head, detail] = [action.split(':')[0]!, action.includes(':') ? action.slice(action.indexOf(':') + 1) : '']
  if (head === 'LOGIN_FAILED') {
    return `Failed sign-in attempt (${LOGIN_FAILURES[detail] ?? humanize(detail).toLowerCase()})`
  }
  if (head === 'CASE_SECTION_SAVED') {
    return SECTIONS[detail] ? `Saved the ${SECTIONS[detail]} section of a case` : `Saved a section of a case (${detail || 'unknown'})`
  }
  return humanize(action)
}
