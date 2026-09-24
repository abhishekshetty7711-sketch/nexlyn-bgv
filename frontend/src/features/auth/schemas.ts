import { z } from 'zod'

/**
 * Mirrors the backend password rules (CLAUDE.md §11.4) so people get instant feedback:
 * at least 12 characters and at least 3 of the 4 character types. The server additionally rejects
 * the email address and well-known passwords, and is always the final judge.
 */
export const MIN_PASSWORD_LENGTH = 12

export function countCharacterTypes(password: string): number {
  const tests = [/[a-z]/, /[A-Z]/, /[0-9]/, /[^a-zA-Z0-9]/]
  return tests.filter((test) => test.test(password)).length
}

export const passwordSchema = z
  .string()
  .min(MIN_PASSWORD_LENGTH, `Use at least ${MIN_PASSWORD_LENGTH} characters`)
  .max(1024, 'That password is too long')
  .refine((value) => countCharacterTypes(value) >= 3, 'Mix at least 3 of: lowercase, uppercase, digits, symbols')

export const loginSchema = z.object({
  email: z.string().trim().min(1, 'Enter your email address').pipe(z.email('Enter a valid email address')),
  password: z.string().min(1, 'Enter your password'),
})
export type LoginValues = z.infer<typeof loginSchema>

/** A 6-digit authenticator code, or a backup code such as ABCDE-FGHJK. */
export const codeSchema = z.object({
  code: z.string().trim().min(6, 'Enter the 6-digit code from your authenticator app').max(32, 'That code is too long'),
})
export type CodeValues = z.infer<typeof codeSchema>

export const acceptInvitationSchema = z
  .object({
    fullName: z.string().trim().min(1, 'Enter your full name').max(200, 'That name is too long'),
    password: passwordSchema,
    confirmPassword: z.string(),
  })
  .refine((values) => values.password === values.confirmPassword, {
    path: ['confirmPassword'],
    message: 'The two passwords do not match',
  })
export type AcceptInvitationValues = z.infer<typeof acceptInvitationSchema>

export const changePasswordSchema = z
  .object({
    currentPassword: z.string().min(1, 'Enter your current password'),
    newPassword: passwordSchema,
    confirmPassword: z.string(),
  })
  .refine((values) => values.newPassword === values.confirmPassword, {
    path: ['confirmPassword'],
    message: 'The two passwords do not match',
  })
  .refine((values) => values.newPassword !== values.currentPassword, {
    path: ['newPassword'],
    message: 'Choose a password different from your current one',
  })
export type ChangePasswordValues = z.infer<typeof changePasswordSchema>
