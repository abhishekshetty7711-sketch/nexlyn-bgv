import type { HTMLAttributes } from 'react'
import { cn } from '@/lib/utils'

export function Card({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div data-slot="card" className={cn('rounded-xl border border-line bg-white p-4 shadow-sm', className)} {...props} />
}
