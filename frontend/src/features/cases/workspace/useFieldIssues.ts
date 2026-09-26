import { useCallback } from 'react'
import type { FieldIssue } from '@/components/ui/field'
import { useValidation } from '../api'

/**
 * What the server's checklist (CLAUDE.md section 7.1) says about the fields of one section, so the message can sit next
 * to the field it is about instead of only in section 8. Errors are what block the report; warnings are what is
 * recommended. `prefix` narrows a check's fields (`check:<id>:`).
 *
 * The checklist describes what is SAVED. While a field has unsaved typing (`typing`) its message is left out, so it never
 * tells someone their name is missing while it is in the box.
 */
export function useFieldIssues(caseId: string, section: string, prefix = '') {
  const validation = useValidation(caseId)
  return useCallback(
    (field: string, typing = false): FieldIssue | undefined => {
      if (typing || !validation.data) {
        return undefined
      }
      const key = `${prefix}${field}`
      const error = validation.data.errors.find((issue) => issue.section === section && issue.field === key)
      if (error) {
        return { message: error.message, level: 'error' }
      }
      const warning = validation.data.warnings.find((issue) => issue.section === section && issue.field === key)
      return warning ? { message: warning.message, level: 'warning' } : undefined
    },
    [validation.data, section, prefix],
  )
}
