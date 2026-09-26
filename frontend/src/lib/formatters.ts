/**
 * The typing helpers of the reference tool (CLAUDE.md section 6.2): names in capitals, Aadhaar in groups of four, PAN in
 * capitals, PIN of six digits, phone as +91 XXXXX XXXXX. Each takes what is in the box and returns what the box should
 * show. They only tidy what is typed; the server checks everything again and stores the plain form.
 */

const digitsOf = (raw: string) => raw.replace(/\D/g, '')

/** Names are printed in capitals on official documents (Aadhaar, PAN), so the boxes take capitals. */
export function upperCase(raw: string): string {
  return raw.toUpperCase()
}

/** A PIN code: six digits at most. */
export function formatPin(raw: string): string {
  return digitsOf(raw).slice(0, 6)
}

/** Aadhaar: twelve digits at most, shown as XXXX XXXX XXXX. */
export function formatAadhaar(raw: string): string {
  const digits = digitsOf(raw).slice(0, 12)
  return [digits.slice(0, 4), digits.slice(4, 8), digits.slice(8, 12)].filter(Boolean).join(' ')
}

/** PAN: five letters, four digits, one letter, in capitals; spaces and symbols are dropped. */
export function formatPan(raw: string): string {
  return raw.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 10)
}

/** UAN: twelve digits at most. */
export function formatUan(raw: string): string {
  return digitsOf(raw).slice(0, 12)
}

/**
 * An Indian mobile number as +91 XXXXX XXXXX while it is typed. The +91 is put in front for you. A number pasted with
 * +91, 0091, 91 or a leading 0 is accepted (the country part is dropped); ten digits at most are kept. An empty box
 * stays empty.
 */
export function formatPhone(raw: string): string {
  let digits = digitsOf(raw)
  const text = raw.trim()
  if (text.startsWith('+91') || text.startsWith('0091')) {
    digits = digitsOf(text.slice(text.startsWith('+') ? 3 : 4))
  } else if (digits.length > 10 && digits.startsWith('91')) {
    digits = digits.slice(2)
  } else if (digits.length > 10 && digits.startsWith('0')) {
    digits = digits.slice(1)
  }
  digits = digits.slice(0, 10)
  if (digits === '') {
    return ''
  }
  return digits.length > 5 ? `+91 ${digits.slice(0, 5)} ${digits.slice(5)}` : `+91 ${digits}`
}
