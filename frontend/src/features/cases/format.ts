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

/** A save answered 409 because someone else changed the case first. */
export function isStaleVersion(error: unknown): boolean {
  return typeof error === 'object' && error !== null && 'status' in error && (error as { status: number }).status === 409
}
