import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { AuthShell } from './AuthShell'

describe('AuthShell', () => {
  it('has exactly one main landmark, the brand heading and the given subtitle', () => {
    render(<AuthShell subtitle="Sign in to the admin console">form</AuthShell>)
    expect(screen.getAllByRole('main')).toHaveLength(1)
    expect(screen.getByRole('heading', { level: 1, name: 'Nexlyn BGV' })).toBeInTheDocument()
    expect(screen.getByText('Sign in to the admin console')).toBeInTheDocument()
    expect(screen.getByText('form')).toBeInTheDocument()
    expect(screen.getByText(/For authorised Nexlyn staff only/)).toBeInTheDocument()
  })

  it('keeps the background photograph out of the accessibility tree', () => {
    render(<AuthShell subtitle="Sign in">form</AuthShell>)
    // A screen reader must announce nothing about it: not a landmark, not an image with a name.
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
    const picture = document.querySelector('picture')
    expect(picture).toBeInTheDocument()
    expect(picture!.closest('[aria-hidden="true"]')).not.toBeNull()
    const img = picture!.querySelector('img')!
    expect(img).toHaveAttribute('alt', '')
    expect(img).toHaveAttribute('aria-hidden', 'true')
  })

  it('loads the background eagerly, at high priority, with a WebP source and a JPEG fallback', () => {
    render(<AuthShell subtitle="Sign in">form</AuthShell>)
    const img = document.querySelector('picture img')!
    expect(img).toHaveAttribute('loading', 'eager')
    expect(img).toHaveAttribute('fetchpriority', 'high')
    expect(img.getAttribute('src')).toMatch(/login-1200\.jpg$/)
    expect(img.getAttribute('srcset')).toMatch(/login-800\.jpg 541w.*login-1200\.jpg 812w.*login-1800\.jpg 1218w/)
    const source = document.querySelector('picture source')!
    expect(source).toHaveAttribute('type', 'image/webp')
    expect(source.getAttribute('srcset')).toMatch(/login-800\.webp 541w.*login-1200\.webp 812w.*login-1800\.webp 1218w/)
  })

  it('shows the brand tagline near the photograph', () => {
    render(<AuthShell subtitle="Sign in">form</AuthShell>)
    expect(screen.getByText('Verify')).toBeInTheDocument()
    expect(screen.getByText('Validate')).toBeInTheDocument()
    expect(screen.getByText('Trust')).toBeInTheDocument()
  })
})
