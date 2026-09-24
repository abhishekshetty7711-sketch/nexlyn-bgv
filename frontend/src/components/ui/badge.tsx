import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

const TONES = {
  neutral: 'bg-slate-100 text-slate-700',
  green: 'bg-emerald-100 text-emerald-800',
  red: 'bg-red-100 text-red-800',
  amber: 'bg-amber-100 text-amber-900',
} as const

export function Badge({ tone = 'neutral', children }: { tone?: keyof typeof TONES; children: ReactNode }) {
  return <span className={cn('inline-flex rounded-full px-2 py-0.5 text-xs font-medium', TONES[tone])}>{children}</span>
}
