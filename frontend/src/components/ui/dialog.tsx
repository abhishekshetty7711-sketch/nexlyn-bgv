import { type ReactNode, useEffect, useId } from 'react'

interface DialogProps {
  title: string
  onClose: () => void
  children: ReactNode
  /** `wide` for content such as a report preview. */
  size?: 'normal' | 'wide'
}

/** A simple modal: covers the page, closes on Escape, and is announced to screen readers. */
export function Dialog({ title, onClose, children, size = 'normal' }: DialogProps) {
  const titleId = useId()

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose()
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-slate-900/40 p-4">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className={`max-h-[90vh] w-full overflow-y-auto rounded-lg bg-white p-5 shadow-xl ${size === 'wide' ? 'max-w-5xl' : 'max-w-lg'}`}
      >
        <h2 id={titleId} className="mb-4 text-lg font-semibold text-slate-900">
          {title}
        </h2>
        {children}
      </div>
    </div>
  )
}
