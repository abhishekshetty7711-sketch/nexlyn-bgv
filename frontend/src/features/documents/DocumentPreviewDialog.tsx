import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { DocumentImage } from './DocumentImage'
import type { DocumentView } from './types'

/** The zoom steps of the reference tool's picture viewer: 50% to 300% in steps of 25%. */
export const ZOOM_MIN = 50
export const ZOOM_MAX = 300
export const ZOOM_STEP = 25

interface DocumentPreviewDialogProps {
  document: DocumentView
  onClose: () => void
  /** Opens the picture in its own browser tab (for a very large picture). */
  onOpenInTab: () => void
}

/**
 * A picture at a size you can read, with zoom in, zoom out and reset. The zoom is only for looking: it changes nothing on the
 * report (the crop and the page placement do). The whole picture is shown, not the cropped part.
 */
export function DocumentPreviewDialog({ document, onClose, onOpenInTab }: DocumentPreviewDialogProps) {
  const [zoom, setZoom] = useState(100)
  const clamp = (value: number) => Math.min(ZOOM_MAX, Math.max(ZOOM_MIN, value))

  return (
    <Dialog title={`View ${document.displayLabel}`} size="wide" onClose={onClose}>
      <div className="flex flex-col gap-3">
        <div className="flex flex-wrap items-center gap-2" role="group" aria-label="Zoom">
          <Button type="button" size="sm" variant="outline" disabled={zoom <= ZOOM_MIN} onClick={() => setZoom((value) => clamp(value - ZOOM_STEP))} aria-label="Zoom out">
            −
          </Button>
          <span role="status" aria-label="Zoom level" className="min-w-12 text-center text-sm font-medium text-slate-800">
            {zoom}%
          </span>
          <Button type="button" size="sm" variant="outline" disabled={zoom >= ZOOM_MAX} onClick={() => setZoom((value) => clamp(value + ZOOM_STEP))} aria-label="Zoom in">
            +
          </Button>
          <Button type="button" size="sm" variant="ghost" disabled={zoom === 100} onClick={() => setZoom(100)}>
            Reset zoom
          </Button>
          <span className="flex-1" />
          <Button type="button" size="sm" variant="outline" onClick={onOpenInTab}>
            Open in a new tab
          </Button>
        </div>
        <div className="max-h-[65vh] overflow-auto rounded border border-slate-200 bg-slate-50 p-2" tabIndex={0} aria-label="Picture, scrollable">
          <div style={{ width: `${zoom}%` }} data-testid="zoomed-picture">
            <DocumentImage documentId={document.id} alt={document.displayLabel} className="block w-full" />
          </div>
        </div>
        <p className="text-xs text-slate-500">Zooming is only for looking; it does not change the report. Use Edit to choose the part of the picture that is printed.</p>
        <div className="flex justify-end">
          <Button type="button" variant="outline" onClick={onClose}>
            Close
          </Button>
        </div>
      </div>
    </Dialog>
  )
}
