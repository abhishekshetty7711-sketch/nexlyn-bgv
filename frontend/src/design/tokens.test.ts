/// <reference types="node" />
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * The colours of the dashboard must stay readable. This reads the design tokens from src/index.css and checks the pairs
 * that carry text or the outline of a control against the WCAG 2.2 minimums: 4.5:1 for normal text, 3:1 for the outline of a
 * control and for large text. The greys and status colours are Tailwind's standard palette (hex values written here).
 */

// The source file, not the compiled CSS: Tailwind leaves out theme variables nothing uses.
const css = readFileSync(resolve(import.meta.dirname, '../index.css'), 'utf8')

function token(name: string): string {
  const match = new RegExp(`--color-${name}:\\s*(#[0-9a-fA-F]{6})`).exec(css)
  if (!match) throw new Error(`token --color-${name} not found in index.css`)
  return match[1]!.toLowerCase()
}

function luminance(hex: string): number {
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255).map((v) => (v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4)))
  return 0.2126 * r! + 0.7152 * g! + 0.0722 * b!
}

function contrast(a: string, b: string): number {
  const [lighter, darker] = [luminance(a), luminance(b)].sort((x, y) => y - x)
  return (lighter! + 0.05) / (darker! + 0.05)
}

// Tailwind's standard palette, as used by the components.
const WHITE = '#ffffff'
const PAGE = '#f8fafc' // slate-50, the page background
const SLATE_100 = '#f1f5f9'
const SLATE_500 = '#64748b'
const SLATE_600 = '#475569'
const SLATE_700 = '#334155'
const SLATE_900 = '#0f172a'

describe('design tokens are readable', () => {
  it('the brand navy is the report\'s navy', () => {
    expect(token('brand-800')).toBe('#0c2d6b')
  })

  it.each([
    ['white text on a brand button', WHITE, () => token('brand-800')],
    ['white text on a brand button (hover)', WHITE, () => token('brand-700')],
    ['brand text on white', () => token('brand-800'), WHITE],
    ['brand link on white', () => token('brand-600'), WHITE],
    ['brand text on the light brand fill of a selected item', () => token('brand-800'), () => token('brand-100')],
    ['brand text on the palest brand fill', () => token('brand-800'), () => token('brand-50')],
    ['menu text on the brand sidebar', () => token('brand-100'), () => token('brand-800')],
    ['small menu text on the brand sidebar', () => token('brand-200'), () => token('brand-800')],
    ['white text on the brand sidebar', WHITE, () => token('brand-800')],
    ['body text on white', SLATE_900, WHITE],
    ['secondary text on white', SLATE_600, WHITE],
    ['muted text on white', SLATE_500, WHITE],
    ['muted text on the page background', SLATE_500, PAGE],
    ['secondary text on a light grey fill', SLATE_600, SLATE_100],
  ] as const)('%s has at least 4.5:1', (_name, foreground, background) => {
    const fg = typeof foreground === 'function' ? foreground() : foreground
    const bg = typeof background === 'function' ? background() : background
    expect(contrast(fg, bg)).toBeGreaterThanOrEqual(4.5)
  })

  it.each([
    ['the outline of a field on white', WHITE],
    ['the outline of a field on the page background', PAGE],
  ])('%s has at least 3:1', (_name, surface) => {
    expect(contrast(token('field'), surface)).toBeGreaterThanOrEqual(3)
  })

  it('the focus ring is visible on white and on the brand fill of a selected item', () => {
    expect(contrast(token('brand-600'), WHITE)).toBeGreaterThanOrEqual(3)
    expect(contrast(token('brand-600'), token('brand-50'))).toBeGreaterThanOrEqual(3)
  })

  // Status colours (badges, alerts): text on its own tint.
  it.each([
    ['success', '#065f46', '#d1fae5'],
    ['danger', '#991b1b', '#fee2e2'],
    ['warning', '#78350f', '#fef3c7'],
    ['info', '#075985', '#e0f2fe'],
    ['neutral', SLATE_700, SLATE_100],
  ])('the %s tone has at least 4.5:1', (_name, text, tint) => {
    expect(contrast(text, tint)).toBeGreaterThanOrEqual(4.5)
  })
})
