import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { pageTitle, usePageTitle } from './usePageTitle'

function Screen({ name }: { name?: string | null }) {
  usePageTitle(name)
  return null
}

describe('usePageTitle', () => {
  it('names the tab after the screen', () => {
    render(<Screen name="Cases" />)
    expect(document.title).toBe('Cases - Nexlyn BGV')
  })

  it('follows the name when it changes, for example once a case has loaded', () => {
    const { rerender } = render(<Screen />)
    expect(document.title).toBe('Nexlyn BGV')
    rerender(<Screen name="DEMO-2026-0004" />)
    expect(document.title).toBe('DEMO-2026-0004 - Nexlyn BGV')
  })

  it('builds the same text with pageTitle', () => {
    expect(pageTitle('Roles')).toBe('Roles - Nexlyn BGV')
    expect(pageTitle(null)).toBe('Nexlyn BGV')
  })
})
