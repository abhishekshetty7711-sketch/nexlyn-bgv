import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import { DateInput } from './date-input'

/** Holds the value the way a form does, and shows it so a test can read what the form would save. */
function Harness({ initial = '' }: { initial?: string }) {
  const [value, setValue] = useState(initial)
  return (
    <>
      <DateInput id="d" aria-label="When" value={value} onChange={setValue} />
      <output aria-label="held">{value}</output>
      <button type="button" onClick={() => setValue('2030-01-02')}>
        load another
      </button>
    </>
  )
}

describe('DateInput', () => {
  it('shows an ISO date as dd/mm/yyyy', () => {
    render(<Harness initial="2026-06-11" />)
    expect(screen.getByLabelText('When')).toHaveValue('11/06/2026')
  })

  it('formats typed digits and hands over an ISO date once the date is complete and real', async () => {
    render(<Harness />)
    const box = screen.getByLabelText('When')
    await userEvent.type(box, '11062026')
    expect(box).toHaveValue('11/06/2026')
    expect(screen.getByLabelText('held')).toHaveTextContent('2026-06-11')
  })

  it('hands over the half-typed text, so the form can refuse it instead of saving a wrong date', async () => {
    render(<Harness />)
    await userEvent.type(screen.getByLabelText('When'), '1106')
    expect(screen.getByLabelText('held')).toHaveTextContent('11/06')
  })

  it('does not accept an impossible date as a date', async () => {
    render(<Harness />)
    await userEvent.type(screen.getByLabelText('When'), '31022026')
    expect(screen.getByLabelText('held')).toHaveTextContent('31/02/2026')
  })

  it('hands over an empty value when the box is emptied', async () => {
    render(<Harness initial="2026-06-11" />)
    await userEvent.clear(screen.getByLabelText('When'))
    expect(screen.getByLabelText('held')).toBeEmptyDOMElement()
  })

  it('follows the form when it loads another date', async () => {
    render(<Harness initial="2026-06-11" />)
    await userEvent.click(screen.getByRole('button', { name: 'load another' }))
    expect(screen.getByLabelText('When')).toHaveValue('02/01/2030')
  })

  it('accepts a pasted date with dashes', async () => {
    render(<Harness />)
    const box = screen.getByLabelText('When')
    await userEvent.click(box)
    await userEvent.paste('11-06-2026')
    expect(box).toHaveValue('11/06/2026')
    expect(screen.getByLabelText('held')).toHaveTextContent('2026-06-11')
  })
})
