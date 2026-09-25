import { describe, expect, it } from 'vitest'
import { groupPermissions, PERMISSION_GROUPS, permissionInfo } from './permissionCatalog'

// The permission codes of CLAUDE.md 11.2 (seeded by the auth migrations).
const ALL_CODES = [
  'CASE_CREATE', 'CASE_READ_ALL', 'CASE_READ_ASSIGNED', 'CASE_UPDATE', 'CASE_DELETE', 'CASE_ASSIGN', 'CHECK_UPDATE', 'DOCUMENT_UPLOAD',
  'DOCUMENT_DELETE', 'PII_UNMASK', 'REPORT_GENERATE', 'REPORT_SUBMIT_FOR_REVIEW', 'REPORT_APPROVE', 'REPORT_FINALIZE',
  'REPORT_DOWNLOAD_FINAL', 'ATTESTATION_APPLY', 'CLIENT_MANAGE', 'SETTINGS_MANAGE', 'USER_MANAGE', 'ROLE_MANAGE', 'AUDIT_READ',
]

describe('permissionInfo', () => {
  it('has a plain name and a description for every permission of the system, none of them the raw code', () => {
    for (const code of ALL_CODES) {
      const info = permissionInfo(code)
      expect(info.label, code).not.toBe(code)
      expect(info.label, code).not.toMatch(/_/)
      expect(info.description.length, code).toBeGreaterThan(10)
    }
  })

  it('puts every permission in exactly one area', () => {
    const listed = PERMISSION_GROUPS.flatMap((group) => group.codes)
    expect([...listed].sort()).toEqual([...ALL_CODES].sort())
    expect(new Set(listed).size).toBe(listed.length)
  })

  it('falls back to the server\'s description, then to the code, for a permission it does not know', () => {
    expect(permissionInfo('NEW_THING', 'Do the new thing')).toEqual({ label: 'NEW_THING', description: 'Do the new thing' })
    expect(permissionInfo('NEW_THING')).toEqual({ label: 'NEW_THING', description: '' })
  })
})

describe('groupPermissions', () => {
  it('groups by area in a fixed order, leaving out empty areas', () => {
    const groups = groupPermissions(['AUDIT_READ', 'CASE_CREATE', 'REPORT_APPROVE', 'CASE_READ_ALL'], (code) => code)
    expect(groups.map((group) => group.title)).toEqual(['Cases', 'Reports and review', 'Administration'])
    expect(groups[0]!.items).toEqual(['CASE_CREATE', 'CASE_READ_ALL']) // the order of the area, not of the input
  })

  it('collects codes it does not know under "Other", last', () => {
    const groups = groupPermissions(['NEW_THING', 'CASE_CREATE'], (code) => code)
    expect(groups.map((group) => group.title)).toEqual(['Cases', 'Other'])
    expect(groups[1]!.items).toEqual(['NEW_THING'])
  })

  it('is empty for nothing', () => {
    expect(groupPermissions([], (code: string) => code)).toEqual([])
  })
})
