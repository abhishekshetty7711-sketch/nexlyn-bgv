import type { CaseView } from '../types'

/** What every editable section receives from the workspace. */
export interface SectionProps {
  caseView: CaseView
  /** False when the case is locked or the admin may not edit it: fields are read-only, no Save button. */
  canEdit: boolean
  /** Fetches the case again (after a conflict). */
  onReload: () => void
}
