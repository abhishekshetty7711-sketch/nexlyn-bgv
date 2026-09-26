import { type ChangeEvent, useLayoutEffect, useRef } from 'react'
import { type Control, type FieldValues, type Path, useController } from 'react-hook-form'
import { Input } from './input'

interface FormattedFieldProps<T extends FieldValues> {
  control: Control<T>
  name: Path<T>
  id: string
  /** Takes what is in the box and returns what it should show (see lib/formatters). */
  format: (raw: string) => string
  /** True when the format never changes the length (capitals): the cursor then stays where it was. */
  keepCaret?: boolean
  disabled?: boolean
  inputMode?: 'text' | 'numeric' | 'tel'
  maxLength?: number
  'aria-describedby'?: string
  'aria-invalid'?: boolean | 'true' | 'false'
}

/**
 * A text box that tidies what is typed (capitals, groups of digits) as the reference tool's boxes did. Put it directly
 * inside `Field`, which passes on the error wiring. The form holds the tidied text.
 */
export function FormattedField<T extends FieldValues>({ control, name, id, format, keepCaret = false, disabled, inputMode, maxLength, ...aria }: FormattedFieldProps<T>) {
  const { field } = useController({ control, name })
  const element = useRef<HTMLInputElement | null>(null)
  const caret = useRef<number | null>(null)

  // Putting the tidied text back moves the cursor to the end; capitals keep the cursor where the person was typing.
  useLayoutEffect(() => {
    if (caret.current !== null && element.current) {
      element.current.setSelectionRange(caret.current, caret.current)
      caret.current = null
    }
  })

  function change(event: ChangeEvent<HTMLInputElement>) {
    caret.current = keepCaret ? event.target.selectionStart : null
    field.onChange(format(event.target.value))
  }

  return (
    <Input
      id={id}
      ref={(node) => {
        element.current = node
        field.ref(node)
      }}
      value={typeof field.value === 'string' ? field.value : ''}
      onChange={change}
      onBlur={field.onBlur}
      disabled={disabled}
      inputMode={inputMode}
      maxLength={maxLength}
      autoComplete="off"
      {...aria}
    />
  )
}
