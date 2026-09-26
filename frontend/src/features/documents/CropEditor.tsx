import { type PointerEvent, useRef, useState } from 'react'
import { Button } from '@/components/ui/button'
import { cropFromDrag, FULL_IMAGE, fitCrop, MIN_CROP, percent } from './crop'
import { DocumentImage } from './DocumentImage'
import type { Crop } from './types'

interface CropEditorProps {
  documentId: string
  /** null = show the whole picture. */
  crop: Crop | null
  /** What is saved now, so the changes made here can be taken back with Undo. */
  savedCrop?: Crop | null
  onChange: (crop: Crop | null) => void
}

const SLIDERS: { key: keyof Crop; label: string; min: number }[] = [
  { key: 'x', label: 'Left edge', min: 0 },
  { key: 'y', label: 'Top edge', min: 0 },
  { key: 'width', label: 'Width', min: Math.ceil(MIN_CROP * 100) },
  { key: 'height', label: 'Height', min: Math.ceil(MIN_CROP * 100) },
]

/**
 * Chooses which part of a picture the report shows (the file itself is never changed). Drag on the
 * picture to draw the part to keep, or fine-tune with the sliders: a narrow crop is a zoom.
 */
export function CropEditor({ documentId, crop, savedCrop = null, onChange }: CropEditorProps) {
  const frame = useRef<HTMLDivElement>(null)
  const [dragStart, setDragStart] = useState<{ x: number; y: number } | null>(null)
  const [dragging, setDragging] = useState<Crop | null>(null)
  const shown = dragging ?? crop

  function point(event: PointerEvent<HTMLDivElement>) {
    const box = frame.current?.getBoundingClientRect()
    if (!box || box.width === 0 || box.height === 0) {
      return { x: 0, y: 0 }
    }
    return { x: (event.clientX - box.left) / box.width, y: (event.clientY - box.top) / box.height }
  }

  function onPointerDown(event: PointerEvent<HTMLDivElement>) {
    event.currentTarget.setPointerCapture?.(event.pointerId)
    setDragStart(point(event))
  }

  function onPointerMove(event: PointerEvent<HTMLDivElement>) {
    if (dragStart) {
      setDragging(cropFromDrag(dragStart, point(event)))
    }
  }

  function onPointerUp(event: PointerEvent<HTMLDivElement>) {
    if (dragStart) {
      const drawn = cropFromDrag(dragStart, point(event))
      if (drawn) {
        onChange(drawn)
      }
    }
    setDragStart(null)
    setDragging(null)
  }

  function setSlider(key: keyof Crop, value: number) {
    const next = { ...(crop ?? FULL_IMAGE), [key]: value / 100 }
    // Moving an edge inwards shrinks the box instead of being refused.
    if (key === 'x') {
      next.width = Math.min(next.width, 1 - next.x)
    }
    if (key === 'y') {
      next.height = Math.min(next.height, 1 - next.y)
    }
    onChange(fitCrop(next))
  }

  return (
    <div className="flex flex-col gap-3">
      <div
        ref={frame}
        data-testid="crop-frame"
        className="relative mx-auto w-full max-w-md cursor-crosshair touch-none select-none overflow-hidden rounded-md border border-slate-300 bg-slate-100"
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={() => {
          setDragStart(null)
          setDragging(null)
        }}
      >
        <DocumentImage documentId={documentId} alt="Picture to crop" className="block w-full" />
        {shown && (
          <div
            data-testid="crop-box"
            className="pointer-events-none absolute border-2 border-white shadow-[0_0_0_9999px_rgba(15,23,42,0.55)]"
            style={{ left: `${shown.x * 100}%`, top: `${shown.y * 100}%`, width: `${shown.width * 100}%`, height: `${shown.height * 100}%` }}
          />
        )}
      </div>
      <p className="text-xs text-slate-500">Drag on the picture to choose the part to show, or use the sliders. Only the report changes; the original file is kept.</p>
      <div className="grid gap-2 sm:grid-cols-2">
        {SLIDERS.map((slider) => {
          const value = percent((crop ?? FULL_IMAGE)[slider.key])
          return (
            <label key={slider.key} className="flex items-center gap-2 text-xs text-slate-700">
              <span className="w-20">{slider.label}</span>
              <input
                type="range"
                min={slider.min}
                max={100}
                value={value}
                aria-label={slider.label}
                aria-valuetext={`${value} percent`}
                onChange={(event) => setSlider(slider.key, Number(event.target.value))}
                className="flex-1"
              />
              <span className="w-10 text-right">{value}%</span>
            </label>
          )
        })}
      </div>
      <div className="flex flex-wrap gap-2">
        <Button type="button" size="sm" variant="outline" disabled={crop === null} onClick={() => onChange(null)}>
          Use the whole picture
        </Button>
        <Button type="button" size="sm" variant="outline" disabled={JSON.stringify(crop) === JSON.stringify(savedCrop)} onClick={() => onChange(savedCrop)}>
          Undo changes
        </Button>
      </div>
    </div>
  )
}
