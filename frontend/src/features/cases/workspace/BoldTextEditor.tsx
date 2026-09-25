import DOMPurify from 'dompurify'
import { useRef } from 'react'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'

interface BoldTextEditorProps {
  id: string
  label: string
  value: string
  onChange: (value: string) => void
  disabled?: boolean
  error?: string
}

/**
 * Remarks may contain bold text and nothing else (CLAUDE.md section 6.2). The text is kept as simple
 * HTML; the Bold button wraps the selection in a strong tag. The preview runs through DOMPurify with
 * only that tag allowed, and the server sanitises again on save, so a pasted script can never run.
 */
export function BoldTextEditor({ id, label, value, onChange, disabled, error }: BoldTextEditorProps) {
  const area = useRef<HTMLTextAreaElement>(null)

  function makeBold() {
    const element = area.current
    if (!element) {
      return
    }
    const { selectionStart, selectionEnd } = element
    const selected = value.slice(selectionStart, selectionEnd)
    onChange(`${value.slice(0, selectionStart)}<strong>${selected}</strong>${value.slice(selectionEnd)}`)
  }

  // This is the only place the app renders HTML, and it is limited to <strong>.
  const preview = DOMPurify.sanitize(value, { ALLOWED_TAGS: ['strong', 'b'], ALLOWED_ATTR: [] })

  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center justify-between">
        <label htmlFor={id} className="text-sm font-medium text-slate-700">
          {label}
        </label>
        <Button type="button" size="sm" variant="outline" disabled={disabled} onClick={makeBold} aria-label={`Bold selected text in ${label}`}>
          <strong>B</strong>
        </Button>
      </div>
      <Textarea
        id={id}
        ref={area}
        rows={6}
        value={value}
        disabled={disabled}
        aria-invalid={!!error}
        onChange={(event) => onChange(event.target.value)}
      />
      {error && (
        <p className="text-xs text-red-600" role="alert">
          {error}
        </p>
      )}
      <div className="rounded-md border border-dashed border-slate-300 bg-slate-50 p-2 text-sm text-slate-700">
        <span className="mb-1 block text-xs uppercase text-slate-500">Preview</span>
        <div aria-label={`${label} preview`} className="whitespace-pre-wrap" dangerouslySetInnerHTML={{ __html: preview }} />
      </div>
    </div>
  )
}
