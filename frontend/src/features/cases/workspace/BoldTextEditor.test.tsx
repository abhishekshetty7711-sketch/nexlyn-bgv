import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import { BoldTextEditor } from './BoldTextEditor'

function Harness({ initial = '', disabled = false }: { initial?: string; disabled?: boolean }) {
  const [value, setValue] = useState(initial)
  return <BoldTextEditor id="ed" label="Remarks" value={value} onChange={setValue} disabled={disabled} />
}

describe('BoldTextEditor', () => {
  it('shows a preview with the bold text rendered', () => {
    render(<Harness initial="All <strong>clear</strong> here" />)
    const preview = screen.getByLabelText('Remarks preview')
    expect(preview.querySelector('strong')).toHaveTextContent('clear')
    expect(preview).toHaveTextContent('All clear here')
  })

  it('gives the named preview a role, because a plain div cannot carry a label', () => {
    render(<Harness initial="text" />)
    expect(screen.getByRole('group', { name: 'Remarks preview' })).toHaveTextContent('text')
  })

  it('never renders anything but bold, whatever is typed or pasted', () => {
    render(<Harness initial={'<img src=x onerror="alert(1)"><script>alert(1)</script><a href="javascript:alert(1)">x</a><strong onclick="x()">ok</strong>'} />)
    const preview = screen.getByLabelText('Remarks preview')
    expect(preview.querySelector('img')).toBeNull()
    expect(preview.querySelector('script')).toBeNull()
    expect(preview.querySelector('a')).toBeNull()
    expect(preview.querySelector('[onclick]')).toBeNull()
    expect(preview.querySelector('strong')).toHaveTextContent('ok')
    expect(document.querySelector('img')).toBeNull()
  })

  it('wraps the selected text in bold when the button is pressed', async () => {
    render(<Harness initial="make this bold please" />)
    const box = screen.getByLabelText('Remarks') as HTMLTextAreaElement
    box.focus()
    box.setSelectionRange(5, 9) // "this"

    await userEvent.click(screen.getByRole('button', { name: 'Bold selected text in Remarks' }))

    expect(box.value).toBe('make <strong>this</strong> bold please')
    expect(screen.getByLabelText('Remarks preview').querySelector('strong')).toHaveTextContent('this')
  })

  it('cannot be edited when disabled', () => {
    render(<Harness initial="fixed" disabled />)
    expect(screen.getByLabelText('Remarks')).toBeDisabled()
    expect(screen.getByRole('button', { name: /Bold selected text/ })).toBeDisabled()
  })
})
