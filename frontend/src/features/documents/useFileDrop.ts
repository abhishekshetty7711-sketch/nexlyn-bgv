import type { ClipboardEvent, DragEvent } from 'react'
import { useState } from 'react'

/**
 * Lets an area take files that are dragged onto it, or pictures pasted while it has the focus (a screenshot copied to the
 * clipboard, for instance), as the reference tool's upload boxes did. Spread `props` on the area and use `over` to show that
 * a file is above it. `onFiles` receives every file dropped or pasted; the caller checks them like any chosen file.
 */
export function useFileDrop(onFiles: (files: File[]) => void, enabled: boolean) {
  const [over, setOver] = useState(false)
  const carriesFiles = (event: DragEvent) => Array.from(event.dataTransfer?.types ?? []).includes('Files')

  const props = {
    onDragEnter: (event: DragEvent) => {
      if (enabled && carriesFiles(event)) {
        event.preventDefault()
        setOver(true)
      }
    },
    onDragOver: (event: DragEvent) => {
      if (enabled && carriesFiles(event)) {
        event.preventDefault() // without this the browser would open the file instead of dropping it here
        setOver(true)
      }
    },
    onDragLeave: (event: DragEvent) => {
      if (!event.currentTarget.contains(event.relatedTarget as Node | null)) {
        setOver(false)
      }
    },
    onDrop: (event: DragEvent) => {
      setOver(false)
      if (!enabled) {
        return
      }
      const files = Array.from(event.dataTransfer?.files ?? [])
      if (files.length > 0) {
        event.preventDefault()
        onFiles(files)
      }
    },
    onPaste: (event: ClipboardEvent) => {
      if (!enabled) {
        return
      }
      const files = Array.from(event.clipboardData?.files ?? [])
      if (files.length > 0) {
        event.preventDefault() // a pasted picture; pasted text is left alone
        onFiles(files)
      }
    },
  }
  return { over, props }
}
