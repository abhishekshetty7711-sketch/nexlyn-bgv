import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { useAuth } from './AuthContext'
import { authApi } from './authApi'
import { describeAuthError } from './errors'
import { type ChangePasswordValues, changePasswordSchema } from './schemas'

/** Changing your own password ends every session, this one included: you sign in again afterwards. */
export function ChangePasswordPage() {
  const { signOut } = useAuth()
  const [error, setError] = useState<string | null>(null)
  const form = useForm<ChangePasswordValues>({
    resolver: zodResolver(changePasswordSchema),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
  })

  async function submit(values: ChangePasswordValues) {
    setError(null)
    try {
      await authApi.changePassword(values.currentPassword, values.newPassword)
      await signOut('password-changed') // the server already ended the sessions; this clears the page
    } catch (problem) {
      setError(describeAuthError(problem))
      form.resetField('currentPassword')
    }
  }

  return (
    <div className="flex max-w-md flex-col gap-4">
      <h1 className="text-2xl font-semibold text-slate-900">Change password</h1>
      <Card>
        <form className="flex flex-col gap-3" onSubmit={form.handleSubmit(submit)} noValidate>
          <Alert variant="info">You will be signed out everywhere and asked to sign in again with the new password.</Alert>
          {error && <Alert variant="error">{error}</Alert>}
          <Field label="Current password" htmlFor="pw-current" error={form.formState.errors.currentPassword?.message}>
            <Input
              id="pw-current"
              type="password"
              autoComplete="current-password"
              aria-invalid={!!form.formState.errors.currentPassword}
              {...form.register('currentPassword')}
            />
          </Field>
          <Field
            label="New password"
            htmlFor="pw-new"
            hint="At least 12 characters, mixing at least 3 of: lowercase, uppercase, digits, symbols."
            error={form.formState.errors.newPassword?.message}
          >
            <Input
              id="pw-new"
              type="password"
              autoComplete="new-password"
              aria-invalid={!!form.formState.errors.newPassword}
              {...form.register('newPassword')}
            />
          </Field>
          <Field label="Confirm new password" htmlFor="pw-confirm" error={form.formState.errors.confirmPassword?.message}>
            <Input
              id="pw-confirm"
              type="password"
              autoComplete="new-password"
              aria-invalid={!!form.formState.errors.confirmPassword}
              {...form.register('confirmPassword')}
            />
          </Field>
          <Button type="submit" disabled={form.formState.isSubmitting}>
            Change password
          </Button>
        </form>
      </Card>
    </div>
  )
}
