import { MAX_UPLOAD_BYTES } from './types'

const IMAGE_TYPES = ['image/jpeg', 'image/png']
const IMAGE_EXTENSIONS = /\.(jpe?g|png)$/i

/**
 * A quick look before uploading, so an obvious mistake is caught without a round trip. The server
 * decides for real from the file's contents; this only reads the name and the type the browser reports.
 */
export function problemWithFile(file: File, allowPdf: boolean): string | null {
  if (file.size === 0) {
    return `${file.name} is empty.`
  }
  if (file.size > MAX_UPLOAD_BYTES) {
    return `${file.name} is larger than ${MAX_UPLOAD_BYTES / (1024 * 1024)} MB.`
  }
  const isImage = IMAGE_TYPES.includes(file.type) || (file.type === '' && IMAGE_EXTENSIONS.test(file.name))
  const isPdf = file.type === 'application/pdf' || (file.type === '' && /\.pdf$/i.test(file.name))
  if (!isImage && !(allowPdf && isPdf)) {
    return allowPdf ? `${file.name} is not a JPEG, PNG or PDF file.` : `${file.name} is not a JPEG or PNG picture.`
  }
  return null
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`
  }
  if (bytes < 1024 * 1024) {
    return `${Math.round(bytes / 1024)} KB`
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

/** What the file picker should offer. */
export const ACCEPT_PICTURES = 'image/jpeg,image/png'
export const ACCEPT_DOCUMENTS = 'image/jpeg,image/png,application/pdf'
