import { useCallback, useState } from 'react'
import { ApiError } from '@/api/httpClient'
import { describeError } from '@/api/errors'
import { type SaveSection, useSaveSection } from '../api'
import type { CaseView } from '../types'

export interface SectionSaving {
  /** Sends the values with the current version. Resolves true when the save went through. */
  save: (values: Record<string, unknown>) => Promise<boolean>
  isSaving: boolean
  /** Plain-language reason the last save failed, if it did. */
  errorMessage: string | null
  /** True when the failure was a conflict (someone else saved first, or the case got locked): reload. */
  conflict: boolean
  /** True right after a successful save, until the next edit starts. */
  justSaved: boolean
  clearFeedback: () => void
}

/** The save half of every section: version handling, error text, and the "Saved" flash. */
export function useSectionSaving(caseView: CaseView, section: SaveSection): SectionSaving {
  const mutation = useSaveSection(caseView.id, section)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [conflict, setConflict] = useState(false)
  const [justSaved, setJustSaved] = useState(false)

  const save = useCallback(
    async (values: Record<string, unknown>) => {
      setErrorMessage(null)
      setConflict(false)
      try {
        await mutation.mutateAsync({ version: caseView.version, values })
        setJustSaved(true)
        return true
      } catch (problem) {
        setJustSaved(false)
        setErrorMessage(describeError(problem))
        setConflict(problem instanceof ApiError && problem.status === 409)
        return false
      }
    },
    [mutation, caseView.version],
  )

  const clearFeedback = useCallback(() => {
    setJustSaved(false)
    setErrorMessage(null)
    setConflict(false)
  }, [])

  return { save, isSaving: mutation.isPending, errorMessage, conflict, justSaved, clearFeedback }
}
