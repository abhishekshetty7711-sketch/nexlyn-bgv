// RFC 6238 one-time codes (SHA-1, 6 digits, 30 s). Only for signing in from automation (--totp-secret);
// people type the code from their authenticator app instead.
import { createHmac } from 'node:crypto'

const ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'

function base32Decode(text) {
  const clean = text.replace(/[\s=-]/g, '').toUpperCase()
  let bits = ''
  for (const ch of clean) {
    const value = ALPHABET.indexOf(ch)
    if (value < 0) {
      throw new Error('the two-step secret is not valid base32')
    }
    bits += value.toString(2).padStart(5, '0')
  }
  const bytes = []
  for (let i = 0; i + 8 <= bits.length; i += 8) {
    bytes.push(parseInt(bits.slice(i, i + 8), 2))
  }
  return Buffer.from(bytes)
}

export function totpCode(secret, atMillis = Date.now()) {
  const counter = Math.floor(atMillis / 1000 / 30)
  const message = Buffer.alloc(8)
  message.writeBigUInt64BE(BigInt(counter))
  const hmac = createHmac('sha1', base32Decode(secret)).update(message).digest()
  const offset = hmac[hmac.length - 1] & 0x0f
  const binary = ((hmac[offset] & 0x7f) << 24) | (hmac[offset + 1] << 16) | (hmac[offset + 2] << 8) | hmac[offset + 3]
  return String(binary % 1_000_000).padStart(6, '0')
}
