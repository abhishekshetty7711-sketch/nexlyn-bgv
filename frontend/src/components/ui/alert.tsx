import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

const STYLES = {
  error: 'border-red-200 bg-red-50 text-red-800',
  info: 'border-sky-200 bg-sky-50 text-sky-800',
  success: 'border-emerald-200 bg-emerald-50 text-emerald-800',
  warning: 'border-amber-200 bg-amber-50 text-amber-900',
} as const

export function Alert({ variant = 'info', children }: { variant?: keyof typeof STYLES; children: ReactNode }) {
  return (
    <div role={variant === 'error' ? 'alert' : 'status'} className={cn('rounded-md border p-3 text-sm', STYLES[variant])}>
      {children}
    </div>
  )
}
