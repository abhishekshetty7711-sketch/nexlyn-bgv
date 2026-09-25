import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

// Text on its own tint, every pair at least 4.5:1 (checked in src/design/tokens.test.ts).
const TONES = {
  neutral: 'bg-slate-100 text-slate-700',
  green: 'bg-emerald-100 text-emerald-800',
  red: 'bg-red-100 text-red-800',
  amber: 'bg-amber-100 text-amber-900',
  blue: 'bg-sky-100 text-sky-800',
  navy: 'bg-brand-100 text-brand-800',
} as const

interface BadgeProps {
  tone?: keyof typeof TONES
  /** A small icon before the words. Decorative: the words always say the same thing, so colour is never the only signal. */
  icon?: ReactNode
  children: ReactNode
}

export function Badge({ tone = 'neutral', icon, children }: BadgeProps) {
  return (
    <span className={cn('inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium', TONES[tone])}>
      {icon && (
        <span aria-hidden className="inline-flex shrink-0">
          {icon}
        </span>
      )}
      {children}
    </span>
  )
}
