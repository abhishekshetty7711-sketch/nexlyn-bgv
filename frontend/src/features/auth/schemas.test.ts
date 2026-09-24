import { describe, expect, it } from 'vitest'
import {
  acceptInvitationSchema,
  changePasswordSchema,
  codeSchema,
  countCharacterTypes,
  loginSchema,
  MIN_PASSWORD_LENGTH,
  passwordSchema,
} from './schemas'

describe('password rules (mirror of the backend)', () => {
  it('needs at least 12 characters, and that minimum never drops below 12', () => {
    expect(MIN_PASSWORD_LENGTH).toBeGreaterThanOrEqual(12)
    expect(passwordSchema.safeParse('Tr1cky-Ora1').success).toBe(false) // 11 characters
    expect(passwordSchema.safeParse('Tr1cky-Ora12').success).toBe(true) // 12 characters
  })

  it('needs at least three of the four character types', () => {
    expect(countCharacterTypes('abcABC123!')).toBe(4)
    expect(countCharacterTypes('lowercaseonly')).toBe(1)
    expect(passwordSchema.safeParse('alllowercaseletters').success).toBe(false)
    expect(passwordSchema.safeParse('lowerandUPPERonly').success).toBe(false)
    expect(passwordSchema.safeParse('lower-UPPER-symbols').success).toBe(true)
  })

  it('explains what is wrong in plain words', () => {
    const short = passwordSchema.safeParse('Ab1!')
    expect(short.success ? '' : short.error.issues.map((issue) => issue.message).join(' ')).toContain('at least 12')
  })
})

describe('login and code forms', () => {
  it('needs a valid email and a password', () => {
    expect(loginSchema.safeParse({ email: '', password: 'x' }).success).toBe(false)
    expect(loginSchema.safeParse({ email: 'not-an-email', password: 'x' }).success).toBe(false)
    expect(loginSchema.safeParse({ email: 'a@b.co', password: '' }).success).toBe(false)
    expect(loginSchema.safeParse({ email: '  a@b.co ', password: 'x' }).success).toBe(true)
  })

  it('accepts a 6-digit code or a longer backup code, but not something too short', () => {
    expect(codeSchema.safeParse({ code: '123456' }).success).toBe(true)
    expect(codeSchema.safeParse({ code: 'ABCDE-FGHJK' }).success).toBe(true)
    expect(codeSchema.safeParse({ code: '12345' }).success).toBe(false)
  })
})

describe('password forms', () => {
  it('requires the confirmation to match when accepting an invitation', () => {
    const base = { fullName: 'Ada', password: 'Tr1cky-Orange-Kettle' }
    expect(acceptInvitationSchema.safeParse({ ...base, confirmPassword: 'Tr1cky-Orange-Kettle' }).success).toBe(true)
    const mismatch = acceptInvitationSchema.safeParse({ ...base, confirmPassword: 'different' })
    expect(mismatch.success).toBe(false)
    expect(mismatch.success ? [] : mismatch.error.issues.map((issue) => issue.path.join('.'))).toContain('confirmPassword')
    expect(acceptInvitationSchema.safeParse({ ...base, fullName: '  ', confirmPassword: base.password }).success).toBe(false)
  })

  it('refuses a new password equal to the current one', () => {
    const same = 'Tr1cky-Orange-Kettle'
    expect(changePasswordSchema.safeParse({ currentPassword: same, newPassword: same, confirmPassword: same }).success).toBe(false)
    expect(
      changePasswordSchema.safeParse({ currentPassword: same, newPassword: 'Blue-Whale-Sings-42', confirmPassword: 'Blue-Whale-Sings-42' }).success,
    ).toBe(true)
  })
})
