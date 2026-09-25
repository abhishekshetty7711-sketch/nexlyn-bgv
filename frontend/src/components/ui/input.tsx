import { type InputHTMLAttributes, forwardRef } from 'react'
import { cn } from '@/lib/utils'

/** The look shared by Input, Select and Textarea: a visible outline (3:1), a clear focus state and an error state. */
export const CONTROL_CLASSES =
  'w-full rounded-md border border-field bg-white px-3 text-sm text-slate-900 placeholder:text-slate-500 hover:border-slate-600 focus-visible:border-brand-600 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-600/30 disabled:cursor-not-allowed disabled:bg-slate-100 disabled:opacity-60 aria-[invalid=true]:border-red-600 aria-[invalid=true]:focus-visible:ring-red-600/30'

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(
  ({ className, ...props }, ref) => <input ref={ref} className={cn('h-9', CONTROL_CLASSES, className)} {...props} />,
)
Input.displayName = 'Input'
