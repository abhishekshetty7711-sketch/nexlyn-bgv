import { type SelectHTMLAttributes, forwardRef } from 'react'
import { cn } from '@/lib/utils'
import { CONTROL_CLASSES } from './input'

export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement>>(
  ({ className, children, ...props }, ref) => (
    <select ref={ref} className={cn('h-9', CONTROL_CLASSES, className)} {...props}>
      {children}
    </select>
  ),
)
Select.displayName = 'Select'
