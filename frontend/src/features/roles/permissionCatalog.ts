/**
 * Plain-language names for the permission codes of CLAUDE.md 11.2, grouped by area, so the Roles page reads like a list of
 * things a person may do ("See every case") and not like a list of codes (`CASE_READ_ALL`). This is wording only: the
 * codes stay what the server checks, and nothing here grants or removes anything. A code this table does not know (a
 * permission added on the server later) is shown with the description the server sent, or as the code itself.
 */

export interface PermissionInfo {
  label: string
  description: string
}

export const PERMISSION_GROUPS: readonly { title: string; codes: readonly string[] }[] = [
  { title: 'Cases', codes: ['CASE_CREATE', 'CASE_READ_ALL', 'CASE_READ_ASSIGNED', 'CASE_UPDATE', 'CASE_DELETE', 'CASE_ASSIGN'] },
  { title: 'Checks and documents', codes: ['CHECK_UPDATE', 'DOCUMENT_UPLOAD', 'DOCUMENT_DELETE', 'ATTESTATION_APPLY'] },
  { title: 'Sensitive data', codes: ['PII_UNMASK'] },
  { title: 'Reports and review', codes: ['REPORT_GENERATE', 'REPORT_SUBMIT_FOR_REVIEW', 'REPORT_APPROVE', 'REPORT_FINALIZE', 'REPORT_DOWNLOAD_FINAL'] },
  { title: 'Clients and settings', codes: ['CLIENT_MANAGE', 'SETTINGS_MANAGE'] },
  { title: 'Administration', codes: ['USER_MANAGE', 'ROLE_MANAGE', 'AUDIT_READ'] },
]

const OTHER_GROUP = 'Other'

const INFO: Record<string, PermissionInfo> = {
  CASE_CREATE: { label: 'Create cases', description: 'Start a new case for a client.' },
  CASE_READ_ALL: { label: 'See every case', description: 'Open any case, not only the ones assigned to them.' },
  CASE_READ_ASSIGNED: { label: 'See assigned cases', description: 'Open the cases they are assigned to.' },
  CASE_UPDATE: { label: 'Edit case details', description: 'Change the report info, candidate, period, overview, remarks and settings of a case.' },
  CASE_DELETE: { label: 'Delete cases', description: 'Remove a case from the lists.' },
  CASE_ASSIGN: { label: 'Assign people to cases', description: 'Choose who prepares and who reviews a case.' },
  CHECK_UPDATE: { label: 'Add and edit checks', description: 'Add, change, reorder and remove the checks of a case.' },
  DOCUMENT_UPLOAD: { label: 'Upload documents and photos', description: 'Add photos and supporting documents to a case.' },
  DOCUMENT_DELETE: { label: 'Delete documents and photos', description: 'Remove photos and supporting documents from a case.' },
  ATTESTATION_APPLY: { label: "Use the advocate's attestation", description: 'Turn on the legal attestation (seal and disclaimer) on a check.' },
  PII_UNMASK: { label: 'Reveal full Aadhaar and PAN numbers', description: 'Show a full number for 30 seconds. Every reveal is recorded.' },
  REPORT_GENERATE: { label: 'Generate draft reports', description: 'Make a draft PDF of a case.' },
  REPORT_SUBMIT_FOR_REVIEW: { label: 'Send a case for review', description: 'Hand a finished case to a reviewer.' },
  REPORT_APPROVE: { label: 'Approve, or ask for changes', description: 'Review a submitted case. Whoever prepared or submitted a case can never approve it.' },
  REPORT_FINALIZE: { label: 'Finalize reports', description: 'Make the final, protected report, and reopen a finalized case.' },
  REPORT_DOWNLOAD_FINAL: { label: 'Download final reports', description: 'Save the finalized PDF of a case.' },
  CLIENT_MANAGE: { label: 'Manage clients', description: 'Add and edit the clients that order reports.' },
  SETTINGS_MANAGE: { label: 'Change system settings', description: 'Change settings that apply to the whole system.' },
  USER_MANAGE: { label: 'Manage admins', description: 'Invite, edit, disable and sign out other admins.' },
  ROLE_MANAGE: { label: 'Manage roles', description: 'Create and change roles and what they allow.' },
  AUDIT_READ: { label: 'Read the audit log', description: 'See who did what and when. Nobody can change the log.' },
}

/** The name and one-line description to show for a permission code. */
export function permissionInfo(code: string, serverDescription?: string | null): PermissionInfo {
  return INFO[code] ?? { label: code, description: serverDescription ?? '' }
}

export interface PermissionGroup<T> {
  title: string
  items: T[]
}

/** Groups permissions by area, in the order of `PERMISSION_GROUPS`; codes the table does not know go last under "Other". */
export function groupPermissions<T>(items: readonly T[], codeOf: (item: T) => string): PermissionGroup<T>[] {
  const known = new Set(PERMISSION_GROUPS.flatMap((group) => group.codes))
  const groups: PermissionGroup<T>[] = PERMISSION_GROUPS.map((group) => ({
    title: group.title,
    items: group.codes.flatMap((code) => items.filter((item) => codeOf(item) === code)),
  }))
  const others = items.filter((item) => !known.has(codeOf(item)))
  if (others.length > 0) {
    groups.push({ title: OTHER_GROUP, items: others })
  }
  return groups.filter((group) => group.items.length > 0)
}
