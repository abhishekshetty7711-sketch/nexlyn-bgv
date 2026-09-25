import type { ReactNode } from 'react'
import { AlertCircle, AlertTriangle, CheckCircle2, Info } from 'lucide-react'
import { cn } from '@/lib/utils'

const STYLES = {
  error: 'border-red-300 bg-red-50 text-red-800',
  info: 'border-sky-300 bg-sky-50 text-sky-800',
  success: 'border-emerald-300 bg-emerald-50 text-emerald-800',
  warning: 'border-amber-300 bg-amber-50 text-amber-900',
} as const

const ICONS = { error: AlertCircle, info: Info, success: CheckCircle2, warning: AlertTriangle } as const

/** A message box. The icon and the words carry the meaning, so colour is never the only signal. */
export function Alert({ variant = 'info', children }: { variant?: keyof typeof STYLES; children: ReactNode }) {
  const Icon = ICONS[variant]
  return (
    <div role={variant === 'error' ? 'alert' : 'status'} className={cn('flex items-start gap-2.5 rounded-md border p-3 text-sm', STYLES[variant])}>
      <Icon className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
      <div className="min-w-0 flex-1">{children}</div>
    </div>
  )
}
