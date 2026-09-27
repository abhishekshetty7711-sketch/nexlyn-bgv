import type { ReactNode } from 'react'
import logoUrl from '@/assets/nexlyn-logo.jpg'
import { Card } from '@/components/ui/card'
import { LoginBackground } from './LoginBackground'

interface AuthShellProps {
  /** Which step this is, under the brand name: "Sign in to the admin console", "Accept your invitation", ... */
  subtitle: string
  children: ReactNode
}

/**
 * The frame of every screen before sign-in is complete (sign-in, two-step verification and set-up, invitation):
 * brand header, card, notice. From a tablet up, a photograph fills the right side of the screen (about 40% on a
 * tablet, 55% on a laptop or desktop) and the form sits in a plain white panel on the left; on a phone the same
 * photograph becomes a full-screen background, darkened, with the card floating on top of it.
 */
export function AuthShell({ subtitle, children }: AuthShellProps) {
  return (
    <main className="relative flex min-h-screen flex-col md:flex-row">
      <div className="relative z-10 flex w-full flex-1 flex-col items-center justify-center gap-4 p-4 sm:p-6 md:basis-3/5 md:bg-white md:p-10 lg:basis-[45%] lg:p-14">
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
        <p className="max-w-md rounded-lg bg-slate-950/40 px-4 py-1.5 text-center text-xs text-white shadow-sm backdrop-blur-sm md:bg-transparent md:px-0 md:py-0 md:text-slate-600 md:shadow-none md:backdrop-blur-none">
          For authorised Nexlyn staff only. Your session ends after 30 minutes without activity.
        </p>
      </div>
      <LoginBackground />
    </main>
  )
}
