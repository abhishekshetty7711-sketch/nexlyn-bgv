import type { ReactNode } from 'react'
import logoUrl from '@/assets/nexlyn-logo.jpg'
import { Card } from '@/components/ui/card'

interface AuthShellProps {
  /** Which step this is, under the brand name: "Sign in to the admin console", "Accept your invitation", ... */
  subtitle: string
  children: ReactNode
}

/** The frame of every screen before sign-in is complete (sign-in, invitation, two-step set-up): brand header, card, notice. */
export function AuthShell({ subtitle, children }: AuthShellProps) {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-4 bg-brand-50 p-4">
      <Card className="flex w-full max-w-md flex-col gap-5 p-6 shadow-md sm:p-8">
        <div className="flex items-center gap-3">
          <img src={logoUrl} alt="" className="h-12 w-12 shrink-0 rounded-lg border border-line bg-white p-0.5" />
          <div className="leading-tight">
            <h1 className="text-xl font-semibold text-brand-800">Nexlyn BGV</h1>
            <p className="text-sm text-slate-600">{subtitle}</p>
          </div>
        </div>
        {children}
      </Card>
      <p className="max-w-md text-center text-xs text-slate-600">
        For authorised Nexlyn staff only. Your session ends after 30 minutes without activity.
      </p>
    </main>
  )
}
