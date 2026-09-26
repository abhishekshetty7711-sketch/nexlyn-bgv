import { type ReactElement, type ReactNode, cloneElement, isValidElement } from 'react'
import { AlertCircle } from 'lucide-react'

/** A message from the case checklist about this field: an error blocks the report, a warning only recommends. */
export interface FieldIssue {
  message: string
  level: 'error' | 'warning'
}

interface FieldProps {
  label: string
  htmlFor: string
  error?: string
  /** What the case checklist says about this field (shown when there is no error of the form itself). */
  issue?: FieldIssue
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
export function Field({ label, htmlFor, error, issue, hint, required, keepHint, children }: FieldProps) {
  const showHint = !!hint && (!error || !!keepHint)
  const showIssue = !!issue && !error
  const errorId = `${htmlFor}-error`
  const issueId = `${htmlFor}-issue`
  const hintId = `${htmlFor}-hint`
  const control = isValidElement<ControlProps>(children)
    ? cloneElement(children as ReactElement<ControlProps>, {
        'aria-describedby':
          [children.props['aria-describedby'], error ? errorId : undefined, showIssue ? issueId : undefined, showHint ? hintId : undefined].filter(Boolean).join(' ') || undefined,
        'aria-invalid': error || (showIssue && issue?.level === 'error') ? true : children.props['aria-invalid'],
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
      {showIssue && (
        <p id={issueId} className={`flex items-start gap-1 text-xs font-medium ${issue?.level === 'error' ? 'text-red-700' : 'text-amber-800'}`}>
          <AlertCircle className="mt-px h-3.5 w-3.5 shrink-0" aria-hidden />
          <span>{issue?.message}</span>
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
