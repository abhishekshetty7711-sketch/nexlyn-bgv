const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

/** ISO date (2026-06-11) to 11/06/2026 (numeric) or 11-Jun-2026 (text), the two formats of the report. */
export function formatDate(iso: string | null | undefined, format: 'NUMERIC' | 'TEXT' = 'NUMERIC'): string {
  if (!iso) {
    return ''
  }
  const [year, month, day] = iso.slice(0, 10).split('-')
  if (!year || !month || !day) {
    return iso
  }
  return format === 'TEXT' ? `${day}-${MONTHS[Number(month) - 1] ?? month}-${year}` : `${day}/${month}/${year}`
}

const pad = (value: number) => String(value).padStart(2, '0')

/**
 * A moment (an ISO timestamp from the server) as `25/09/2026, 16:53`: day first, 24-hour clock, in the
 * browser's time zone. The one date-and-time style of the whole dashboard; the browser's own locale is
 * never used, because it would print month-first dates (9/25/2026) on some computers.
 */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) {
    return ''
  }
  const moment = new Date(iso)
  if (Number.isNaN(moment.getTime())) {
    return iso
  }
  return `${pad(moment.getDate())}/${pad(moment.getMonth() + 1)}/${moment.getFullYear()}, ${pad(moment.getHours())}:${pad(moment.getMinutes())}`
}

/** A save answered 409 because someone else changed the case first. */
export function isStaleVersion(error: unknown): boolean {
  return typeof error === 'object' && error !== null && 'status' in error && (error as { status: number }).status === 409
}
