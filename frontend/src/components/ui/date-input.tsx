import { type Ref, useState } from 'react'
import { type Control, type FieldValues, type Path, useController } from 'react-hook-form'
import { displayToIso, isoToDisplay, maskDate } from '@/lib/dateInput'
import { Input } from './input'

interface DateInputProps {
  id: string
  /** The date as yyyy-mm-dd, or '' for none. A half-typed date is passed on as typed, so the form can say it is not a date. */
  value: string
  onChange: (value: string) => void
  onBlur?: () => void
  disabled?: boolean
  inputRef?: Ref<HTMLInputElement>
  'aria-label'?: string
  'aria-describedby'?: string
  'aria-invalid'?: boolean | 'true' | 'false'
}

/**
 * A date typed as dd/mm/yyyy: the slashes appear by themselves. It reports an ISO date once the typing is a real date,
 * an empty string when the box is empty, and the half-typed text in between (which the form's rules then refuse with
 * "Enter the date as dd/mm/yyyy" rather than saving a wrong or empty date).
 */
export function DateInput({ id, value, onChange, onBlur, disabled, inputRef, ...aria }: DateInputProps) {
  // What the person has typed. It is shown while it still says what the form holds; when the form is reset or saved and
  // holds something else, the form's date is shown instead.
  const [typed, setTyped] = useState<string | null>(null)
  const text = typed !== null && (typed === value || displayToIso(typed) === value) ? typed : isoToDisplay(value)

  function change(raw: string) {
    const masked = maskDate(raw)
    setTyped(masked)
    onChange(masked === '' ? '' : (displayToIso(masked) ?? masked))
  }

  return (
    <Input
      id={id}
      ref={inputRef}
      value={text}
      onChange={(event) => change(event.target.value)}
      onBlur={onBlur}
      disabled={disabled}
      placeholder="dd/mm/yyyy"
      inputMode="numeric"
      autoComplete="off"
      maxLength={10}
      {...aria}
    />
  )
}

interface DateFieldProps<T extends FieldValues> {
  control: Control<T>
  name: Path<T>
  id: string
  disabled?: boolean
  'aria-describedby'?: string
  'aria-invalid'?: boolean | 'true' | 'false'
}

/** A DateInput tied to a react-hook-form field. Put it directly inside `Field`, which passes on the error wiring. */
export function DateField<T extends FieldValues>({ control, name, id, disabled, ...aria }: DateFieldProps<T>) {
  const { field } = useController({ control, name })
  return (
    <DateInput
      id={id}
      value={typeof field.value === 'string' ? field.value : ''}
      onChange={field.onChange}
      onBlur={field.onBlur}
      inputRef={field.ref}
      disabled={disabled}
      {...aria}
    />
  )
}
