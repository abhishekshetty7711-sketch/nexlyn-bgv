/// <reference types="node" />
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * Click targets must be at least 24 x 24 CSS px (WCAG 2.2, 2.5.8). jsdom has no layout, so this reads the rules that make them
 * so from src/index.css. The buttons and links that were too small carry Tailwind's `min-h-6` (1.5rem = 24 px); their tests
 * check for it where the component is tested.
 */
const css = readFileSync(resolve(import.meta.dirname, '../index.css'), 'utf8')

function rule(selector: string): string {
  const start = css.indexOf(selector)
  if (start < 0) throw new Error(`rule "${selector}" not found in index.css`)
  return css.slice(start, css.indexOf('}', start))
}

function rem(block: string, property: string): number {
  const match = new RegExp(`${property}:\\s*([0-9.]+)rem`).exec(block)
  if (!match) throw new Error(`${property} in rem not found in: ${block}`)
  return Number(match[1]) * 16
}

describe('click targets', () => {
  it('draws tick boxes and round buttons larger than the browser default of about 13 px', () => {
    const block = rule("input[type='checkbox'],")
    expect(block).toContain("input[type='radio']")
    expect(rem(block, 'width')).toBeGreaterThanOrEqual(20)
    expect(rem(block, 'height')).toBeGreaterThanOrEqual(20)
  })

  it('makes the label around a tick box or a round button at least 24 px tall, so the whole row is a target', () => {
    const block = rule("label:has(> input[type='checkbox']),")
    expect(block).toContain("label:has(> input[type='radio'])")
    expect(rem(block, 'min-height')).toBeGreaterThanOrEqual(24)
  })
})
