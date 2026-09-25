import { type ReactNode, useEffect, useId, useRef } from 'react'

interface DialogProps {
  title: string
  onClose: () => void
  children: ReactNode
  /** `wide` for content such as a report preview. */
  size?: 'normal' | 'wide'
}

const FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'

/**
 * A modal: covers the page, is announced to screen readers, closes on Escape, moves the keyboard focus into itself,
 * keeps Tab inside while it is open and gives the focus back to what had it when it closes.
 */
export function Dialog({ title, onClose, children, size = 'normal' }: DialogProps) {
  const titleId = useId()
  const panel = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const before = document.activeElement instanceof HTMLElement ? document.activeElement : null
    // The dialog itself gets the focus (a screen reader reads its title), not its first control, which could be a destructive
    // button such as "Sign out now"; the first Tab then reaches the first control.
    panel.current?.focus()
    return () => before?.focus()
  }, [])

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose()
        return
      }
      if (event.key === 'Tab' && panel.current) {
        const items = [...panel.current.querySelectorAll<HTMLElement>(FOCUSABLE)]
        if (items.length === 0) {
          event.preventDefault()
          return
        }
        const firstItem = items[0]!
        const lastItem = items[items.length - 1]!
        if (event.shiftKey && (document.activeElement === firstItem || document.activeElement === panel.current)) {
          event.preventDefault()
          lastItem.focus()
        } else if (!event.shiftKey && document.activeElement === lastItem) {
          event.preventDefault()
          firstItem.focus()
        }
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-slate-900/50 p-4">
      <div
        ref={panel}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        className={`max-h-[90vh] w-full overflow-y-auto rounded-xl bg-white p-5 shadow-xl outline-none ${size === 'wide' ? 'max-w-5xl' : 'max-w-lg'}`}
      >
        <h2 id={titleId} className="mb-4 text-lg font-semibold text-slate-900">
          {title}
        </h2>
        {children}
      </div>
    </div>
  )
}
