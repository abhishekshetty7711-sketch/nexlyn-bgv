export type DocumentKind = 'PHOTO' | 'CHECK_DOC' | 'FREE_IMAGE'
export type ImageQuality = 'HIGH' | 'MEDIUM' | 'LOW'

/** The part of a picture to show, as fractions (0 to 1) of its width and height. */
export interface Crop {
  x: number
  y: number
  width: number
  height: number
}

export interface DocumentView {
  id: string
  caseId: string
  checkId: string | null
  kind: DocumentKind
  label: string | null
  /** The label people see: the custom one, or "Original Document" / "Additional Document N". */
  displayLabel: string
  originalFilename: string | null
  mimeType: string
  sizeBytes: number
  width: number | null
  height: number | null
  quality: ImageQuality | null
  moveToNextPage: boolean
  useLargerBox: boolean
  crop: Crop | null
  sortOrder: number
  version: number
  uploadedAt: string
}

/** Same limit as the server (nexlyn.documents.max-bytes); the server checks again. */
export const MAX_UPLOAD_BYTES = 10 * 1024 * 1024

export const QUALITY_LABELS: Record<ImageQuality, string> = { HIGH: 'High quality', MEDIUM: 'Medium quality', LOW: 'Low quality' }
