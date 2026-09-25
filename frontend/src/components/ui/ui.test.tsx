import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { Alert } from './alert'
import { Badge } from './badge'
import { Button } from './button'
import { Dialog } from './dialog'
import { Field } from './field'
import { Input } from './input'
import { SkeletonRows } from './skeleton'

describe('Field', () => {
  it('ties the hint to the control', () => {
    render(
      <Field label="Phone" htmlFor="phone" hint="10 digits">
        <Input id="phone" />
      </Field>,
    )
    expect(screen.getByLabelText('Phone')).toHaveAccessibleDescription('10 digits')
    expect(screen.getByLabelText('Phone')).not.toBeInvalid()
  })

  it('ties the error to the control, marks it invalid and announces it', () => {
    render(
      <Field label="Phone" htmlFor="phone" error="Enter a 10-digit number" hint="10 digits">
        <Input id="phone" />
      </Field>,
    )
    const input = screen.getByLabelText('Phone')
    expect(input).toBeInvalid()
    expect(input).toHaveAccessibleDescription('Enter a 10-digit number')
    expect(screen.getByRole('alert')).toHaveTextContent('Enter a 10-digit number')
    expect(screen.queryByText('10 digits')).not.toBeInTheDocument()
  })

  it('keeps the hint next to the error when asked to (a rule the person needs to fix the mistake), tying both to the control', () => {
    render(
      <Field label="Password" htmlFor="pw" hint="At least 12 characters" error="Use at least 12 characters" keepHint>
        <Input id="pw" />
      </Field>,
    )
    expect(screen.getByText('At least 12 characters')).toBeInTheDocument()
    expect(screen.getByLabelText('Password')).toHaveAccessibleDescription('Use at least 12 characters At least 12 characters')
  })

  it('says "(required)" in the label of a required field, and only there', () => {
    render(
      <>
        <Field label="Client name" htmlFor="name" required>
          <Input id="name" />
        </Field>
        <Field label="Nickname" htmlFor="nick">
          <Input id="nick" />
        </Field>
      </>,
    )
    expect(screen.getByLabelText('Client name (required)')).toBeInTheDocument()
    expect(screen.getByLabelText('Nickname')).toBeInTheDocument()
    expect(screen.queryByLabelText('Nickname (required)')).not.toBeInTheDocument()
  })

  it('keeps a description the control already had', () => {
    render(
      <Field label="Code" htmlFor="code" error="Wrong code">
        <Input id="code" aria-describedby="extra" />
      </Field>,
    )
    expect(screen.getByLabelText('Code').getAttribute('aria-describedby')).toBe('extra code-error')
  })
})

describe('Alert and Badge', () => {
  it('an error is announced as an alert and a notice as a status', () => {
    render(
      <>
        <Alert variant="error">Something failed</Alert>
        <Alert variant="info">For your information</Alert>
      </>,
    )
    expect(screen.getByRole('alert')).toHaveTextContent('Something failed')
    expect(screen.getByRole('status')).toHaveTextContent('For your information')
  })

  it('a badge icon is decorative: the accessible text is the words alone', () => {
    render(<Badge tone="green" icon={<svg data-testid="icon" />}>Approved</Badge>)
    expect(screen.getByText('Approved')).toBeInTheDocument()
    expect(screen.getByTestId('icon').closest('[aria-hidden="true"]')).not.toBeNull()
  })
})

describe('Button', () => {
  it('has a danger look and is disabled properly', async () => {
    const onClick = vi.fn()
    render(
      <Button variant="danger" disabled onClick={onClick}>
        Remove
      </Button>,
    )
    await userEvent.click(screen.getByRole('button', { name: 'Remove' }))
    expect(onClick).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Remove' })).toBeDisabled()
  })
})

describe('SkeletonRows', () => {
  it('is announced once as Loading', () => {
    render(<SkeletonRows rows={3} />)
    expect(screen.getByRole('status', { name: 'Loading' })).toBeInTheDocument()
  })
})

function Harness({ onClose }: { onClose: () => void }) {
  const [open, setOpen] = useState(false)
  return (
    <div>
      <Button onClick={() => setOpen(true)}>Open</Button>
      {open && (
        <Dialog
          title="Sure?"
          onClose={() => {
            onClose()
            setOpen(false)
          }}
        >
          <Button variant="outline">First</Button>
          <Button>Last</Button>
        </Dialog>
      )}
    </div>
  )
}

describe('Dialog', () => {
  it('takes the focus, keeps Tab inside, closes on Escape and gives the focus back', async () => {
    const onClose = vi.fn()
    const user = userEvent.setup()
    render(<Harness onClose={onClose} />)
    const opener = screen.getByRole('button', { name: 'Open' })
    await user.click(opener)

    const dialog = screen.getByRole('dialog', { name: 'Sure?' })
    expect(dialog).toHaveFocus()

    await user.tab()
    expect(screen.getByRole('button', { name: 'First' })).toHaveFocus()
    await user.tab()
    expect(screen.getByRole('button', { name: 'Last' })).toHaveFocus()
    await user.tab() // past the last control: wraps to the first, never out of the dialog
    expect(screen.getByRole('button', { name: 'First' })).toHaveFocus()
    await user.tab({ shift: true }) // and backwards from the first to the last
    expect(screen.getByRole('button', { name: 'Last' })).toHaveFocus()

    await user.keyboard('{Escape}')
    expect(onClose).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(opener).toHaveFocus()
  })
})
