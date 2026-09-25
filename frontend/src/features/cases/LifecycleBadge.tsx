import { Badge } from '@/components/ui/badge'
import { LIFECYCLE_LOOK } from './lifecycleLook'
import { LIFECYCLE_LABELS, type Lifecycle } from './types'

export function LifecycleBadge({ lifecycle }: { lifecycle: Lifecycle }) {
  const { tone, icon: Icon } = LIFECYCLE_LOOK[lifecycle]
  return (
    <Badge tone={tone} icon={<Icon className="h-3 w-3" />}>
      {LIFECYCLE_LABELS[lifecycle]}
    </Badge>
  )
}
