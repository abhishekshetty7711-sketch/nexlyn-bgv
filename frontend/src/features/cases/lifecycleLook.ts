import { CheckCircle2, Eye, Lock, PencilLine, Undo2, type LucideIcon } from 'lucide-react'
import type { Lifecycle } from './types'

type Tone = 'neutral' | 'blue' | 'amber' | 'green' | 'navy'

/** How each stage of a case looks: the words always say it, the icon and colour only help. */
export const LIFECYCLE_LOOK: Record<Lifecycle, { tone: Tone; icon: LucideIcon }> = {
  DRAFT: { tone: 'neutral', icon: PencilLine },
  IN_REVIEW: { tone: 'blue', icon: Eye },
  CHANGES_REQUESTED: { tone: 'amber', icon: Undo2 },
  APPROVED: { tone: 'green', icon: CheckCircle2 },
  FINALIZED: { tone: 'navy', icon: Lock },
}
