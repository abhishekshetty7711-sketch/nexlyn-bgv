import { type TextareaHTMLAttributes, forwardRef } from 'react'
import { cn } from '@/lib/utils'
import { CONTROL_CLASSES } from './input'

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement>>(
  ({ className, ...props }, ref) => <textarea ref={ref} className={cn('min-h-20 py-2', CONTROL_CLASSES, className)} {...props} />,
)
Textarea.displayName = 'Textarea'
