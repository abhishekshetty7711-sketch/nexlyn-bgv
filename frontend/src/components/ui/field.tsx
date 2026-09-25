import { type ReactElement, type ReactNode, cloneElement, isValidElement } from 'react'
import { AlertCircle } from 'lucide-react'

interface FieldProps {
  label: string
  htmlFor: string
  error?: string
  hint?: string
  /** Says "(required)" after the label, so nobody has to fail a save to find out. Leave off for optional fields. */
  required?: boolean
  /** Keep the hint on screen when there is an error too. For a rule the person needs in order to fix the mistake (a password policy). */
  keepHint?: boolean
  children: ReactNode
}

type ControlProps = { 'aria-describedby'?: string; 'aria-invalid'?: boolean | 'true' | 'false' }

/**
 * A label, one control (children), an optional hint and an error message. The hint and the error are tied to the
 * control (`aria-describedby`) and the control is marked invalid, so a screen reader announces them with the field
 * (WCAG: errors are identified in text, next to the field, and associated with it).
 */
export function Field({ label, htmlFor, error, hint, required, keepHint, children }: FieldProps) {
  const showHint = !!hint && (!error || !!keepHint)
  const errorId = `${htmlFor}-error`
  const hintId = `${htmlFor}-hint`
  const control = isValidElement<ControlProps>(children)
    ? cloneElement(children as ReactElement<ControlProps>, {
        'aria-describedby':
          [children.props['aria-describedby'], error ? errorId : undefined, showHint ? hintId : undefined].filter(Boolean).join(' ') || undefined,
        'aria-invalid': error ? true : children.props['aria-invalid'],
      })
    : children

  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={htmlFor} className="text-sm font-medium text-slate-800">
        {label}
        {required && <span className="font-normal text-slate-600"> (required)</span>}
      </label>
      {control}
      {showHint && (
        <p id={hintId} className="text-xs text-slate-600">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="flex items-start gap-1 text-xs font-medium text-red-700" role="alert">
          <AlertCircle className="mt-px h-3.5 w-3.5 shrink-0" aria-hidden />
          <span>{error}</span>
        </p>
      )}
    </div>
  )
}
