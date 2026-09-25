// Verhoeff check digit, the checksum of Aadhaar numbers. Used to build FAKE numbers that pass the format check.

const D = [
  [0, 1, 2, 3, 4, 5, 6, 7, 8, 9],
  [1, 2, 3, 4, 0, 6, 7, 8, 9, 5],
  [2, 3, 4, 0, 1, 7, 8, 9, 5, 6],
  [3, 4, 0, 1, 2, 8, 9, 5, 6, 7],
  [4, 0, 1, 2, 3, 9, 5, 6, 7, 8],
  [5, 9, 8, 7, 6, 0, 4, 3, 2, 1],
  [6, 5, 9, 8, 7, 1, 0, 4, 3, 2],
  [7, 6, 5, 9, 8, 2, 1, 0, 4, 3],
  [8, 7, 6, 5, 9, 3, 2, 1, 0, 4],
  [9, 8, 7, 6, 5, 4, 3, 2, 1, 0],
]
const P = [
  [0, 1, 2, 3, 4, 5, 6, 7, 8, 9],
  [1, 5, 7, 6, 2, 8, 3, 0, 9, 4],
  [5, 8, 0, 3, 7, 9, 6, 1, 4, 2],
  [8, 9, 1, 6, 0, 4, 3, 5, 2, 7],
  [9, 4, 5, 3, 1, 2, 6, 8, 7, 0],
  [4, 2, 8, 6, 5, 7, 3, 9, 0, 1],
  [2, 7, 9, 3, 8, 0, 6, 4, 1, 5],
  [7, 0, 4, 6, 9, 1, 3, 2, 5, 8],
]
const INV = [0, 4, 3, 2, 1, 5, 6, 7, 8, 9]

/** True when the digit string ends with a correct Verhoeff check digit. */
export function isValid(digits) {
  let c = 0
  const reversed = [...digits].reverse()
  for (let i = 0; i < reversed.length; i++) {
    c = D[c][P[i % 8][Number(reversed[i])]]
  }
  return c === 0
}

/** The check digit to append to `digits`. */
export function checkDigit(digits) {
  let c = 0
  const reversed = [...digits].reverse()
  for (let i = 0; i < reversed.length; i++) {
    c = D[c][P[(i + 1) % 8][Number(reversed[i])]]
  }
  return INV[c]
}

/** A 12-digit, format-valid, FAKE Aadhaar number built from an 11-digit prefix that starts with 2 to 9. */
export function fakeAadhaar(prefix11) {
  if (!/^[2-9][0-9]{10}$/.test(prefix11)) {
    throw new Error('the prefix must be 11 digits starting with 2 to 9')
  }
  return prefix11 + checkDigit(prefix11)
}
