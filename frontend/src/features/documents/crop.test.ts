import { describe, expect, it } from 'vitest'
import { cropFromDrag, fitCrop, isWholePicture, MIN_CROP, percent } from './crop'
import { formatBytes, problemWithFile } from './files'
import { MAX_UPLOAD_BYTES } from './types'

describe('crop helpers', () => {
  it('turns a drag into a rectangle whichever way it is drawn', () => {
    const forward = cropFromDrag({ x: 0.2, y: 0.1 }, { x: 0.6, y: 0.5 })
    expect(forward?.x).toBeCloseTo(0.2)
    expect(forward?.height).toBeCloseTo(0.4)
    const reversed = cropFromDrag({ x: 0.6, y: 0.5 }, { x: 0.2, y: 0.1 })
    expect(reversed?.x).toBeCloseTo(0.2)
    expect(reversed?.width).toBeCloseTo(0.4)
  })

  it('keeps a drag inside the picture and ignores accidental clicks', () => {
    const clipped = cropFromDrag({ x: -0.5, y: 0.2 }, { x: 1.5, y: 0.6 })
    expect(clipped?.x).toBe(0)
    expect(clipped?.width).toBe(1)
    expect(clipped?.height).toBeCloseTo(0.4)
    expect(cropFromDrag({ x: 0.5, y: 0.5 }, { x: 0.52, y: 0.9 })).toBeNull()
    expect(cropFromDrag({ x: 0.5, y: 0.5 }, { x: 0.5, y: 0.5 })).toBeNull()
  })

  it('fits any crop into the picture and never thinner than the minimum', () => {
    expect(fitCrop({ x: 0.9, y: 0.9, width: 0.5, height: 0.5 })).toEqual({ x: 0.5, y: 0.5, width: 0.5, height: 0.5 })
    expect(fitCrop({ x: -1, y: 0, width: 0.001, height: 2 })).toEqual({ x: 0, y: 0, width: MIN_CROP, height: 1 })
  })

  it('knows when a crop is the whole picture', () => {
    expect(isWholePicture({ x: 0, y: 0, width: 1, height: 1 })).toBe(true)
    expect(isWholePicture({ x: 0, y: 0, width: 0.9, height: 1 })).toBe(false)
    expect(percent(0.456)).toBe(46)
  })
})

describe('upload pre-checks', () => {
  const file = (name: string, type: string, size = 1000) => new File([new Uint8Array(size)], name, { type })

  it('accepts pictures, and PDFs only where allowed', () => {
    expect(problemWithFile(file('a.jpg', 'image/jpeg'), false)).toBeNull()
    expect(problemWithFile(file('a.png', 'image/png'), true)).toBeNull()
    expect(problemWithFile(file('a.pdf', 'application/pdf'), true)).toBeNull()
    expect(problemWithFile(file('a.pdf', 'application/pdf'), false)).toMatch(/not a JPEG or PNG picture/)
  })

  it('goes by the extension when the browser reports no type', () => {
    expect(problemWithFile(file('scan.JPG', ''), false)).toBeNull()
    expect(problemWithFile(file('scan.pdf', ''), true)).toBeNull()
    expect(problemWithFile(file('scan.exe', ''), true)).toMatch(/not a JPEG, PNG or PDF/)
  })

  it('refuses empty and oversized files, and other types', () => {
    expect(problemWithFile(file('e.jpg', 'image/jpeg', 0), true)).toMatch(/empty/)
    expect(problemWithFile(file('big.jpg', 'image/jpeg', MAX_UPLOAD_BYTES + 1), true)).toMatch(/larger than 10 MB/)
    expect(problemWithFile(file('x.gif', 'image/gif'), true)).toMatch(/not a JPEG/)
  })

  it('shows sizes plainly', () => {
    expect(formatBytes(500)).toBe('500 B')
    expect(formatBytes(2048)).toBe('2 KB')
    expect(formatBytes(3 * 1024 * 1024)).toBe('3.0 MB')
  })
})
