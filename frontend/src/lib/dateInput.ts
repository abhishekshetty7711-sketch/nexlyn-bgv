/**
 * Typing dates as dd/mm/yyyy (the way the reference tool's boxes worked) instead of a browser date picker, whose box
 * shows month-first on a computer set to a US locale. The stored value stays an ISO date (yyyy-mm-dd), so the API
 * never sees the typed form.
 */

/** Keeps the digits and puts the slashes in as you type: 11062026 becomes 11/06/2026. */
export function maskDate(raw: string): string {
  const digits = raw.replace(/\D/g, '').slice(0, 8)
  let out = digits.slice(0, 2)
  if (digits.length >= 3) {
    out += `/${digits.slice(2, 4)}`
  }
  if (digits.length >= 5) {
    out += `/${digits.slice(4, 8)}`
  }
  return out
}

/** True for a real calendar date written as yyyy-mm-dd (so 2026-02-31 is not one). */
export function isValidIsoDate(value: string): boolean {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (!match) {
    return false
  }
  const [year, month, day] = [Number(match[1]), Number(match[2]), Number(match[3])]
  const date = new Date(Date.UTC(year, month - 1, day))
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day
}

/** 11/06/2026 to 2026-06-11, or null when it is not a complete, real date. */
export function displayToIso(text: string): string | null {
  const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(text)
  if (!match) {
    return null
  }
  const iso = `${match[3]}-${match[2]}-${match[1]}`
  return isValidIsoDate(iso) ? iso : null
}

/** 2026-06-11 to 11/06/2026. Anything that is not an ISO date is returned as it is (a half-typed value). */
export function isoToDisplay(value: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  return match ? `${match[3]}/${match[2]}/${match[1]}` : value
}
