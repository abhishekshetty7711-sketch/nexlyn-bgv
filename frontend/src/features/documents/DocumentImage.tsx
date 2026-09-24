import { cn } from '@/lib/utils'
import { useDocumentImage } from './api'

interface DocumentImageProps {
  documentId: string
  alt: string
  className?: string
}

/** A stored picture, fetched with the sign-in token (an image tag alone could not send it). */
export function DocumentImage({ documentId, alt, className }: DocumentImageProps) {
  const image = useDocumentImage(documentId)

  if (image.isError) {
    return (
      <span role="img" aria-label={`${alt} (could not be loaded)`} className={cn('flex items-center justify-center bg-slate-100 text-xs text-slate-500', className)}>
        Could not load
      </span>
    )
  }
  if (!image.data) {
    return <span role="img" aria-label={`${alt} (loading)`} className={cn('animate-pulse bg-slate-200', className)} />
  }
  return <img src={image.data} alt={alt} className={cn('object-contain', className)} draggable={false} />
}
