import type { Crop } from './types'

/** Smallest crop side, as a fraction; the server refuses anything thinner. */
export const MIN_CROP = 0.05

export const FULL_IMAGE: Crop = { x: 0, y: 0, width: 1, height: 1 }

const clamp = (value: number, low: number, high: number) => Math.min(high, Math.max(low, value))

/** Keeps a crop inside the picture and at least MIN_CROP wide and tall. */
export function fitCrop(crop: Crop): Crop {
  const width = clamp(crop.width, MIN_CROP, 1)
  const height = clamp(crop.height, MIN_CROP, 1)
  return { x: clamp(crop.x, 0, 1 - width), y: clamp(crop.y, 0, 1 - height), width, height }
}

/** The rectangle drawn by dragging from one point to another (fractions of the picture), or null if too small. */
export function cropFromDrag(start: { x: number; y: number }, end: { x: number; y: number }): Crop | null {
  const x1 = clamp(Math.min(start.x, end.x), 0, 1)
  const y1 = clamp(Math.min(start.y, end.y), 0, 1)
  const x2 = clamp(Math.max(start.x, end.x), 0, 1)
  const y2 = clamp(Math.max(start.y, end.y), 0, 1)
  if (x2 - x1 < MIN_CROP || y2 - y1 < MIN_CROP) {
    return null
  }
  return { x: x1, y: y1, width: x2 - x1, height: y2 - y1 }
}

/** True when the crop is (practically) the whole picture, so it can be stored as "no crop". */
export function isWholePicture(crop: Crop): boolean {
  return crop.x < 0.001 && crop.y < 0.001 && crop.width > 0.999 && crop.height > 0.999
}

/** Rounds to whole percent for display in sliders and text. */
export const percent = (fraction: number) => Math.round(fraction * 100)
