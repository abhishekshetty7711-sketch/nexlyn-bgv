import { useCallback, useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { useAuth } from './AuthContext'
import { authApi } from './authApi'
import { describeAuthError } from './errors'
import { type AcceptInvitationValues, acceptInvitationSchema } from './schemas'
import { TwoFactorSetup } from './TwoFactorSetup'
import type { Challenge, TokenResponse } from './types'

/**
 * The page behind an invitation link (`/accept-invite?token=...`): choose a name and a password,
 * then set up two-factor authentication, exactly like a first login.
 */
export function AcceptInvitationPage() {
  const { state, completeSignIn } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  // Read the token once and remove it from the address bar, so it does not linger in the browser
  // history or leak through a Referer header.
  const [inviteToken] = useState(() => params.get('token'))
  const [challenge, setChallenge] = useState<Challenge | null>(null)
  const [error, setError] = useState<string | null>(null)

  const form = useForm<AcceptInvitationValues>({
    resolver: zodResolver(acceptInvitationSchema),
    defaultValues: { fullName: '', password: '', confirmPassword: '' },
  })

  useEffect(() => {
    if (params.has('token')) {
      navigate('/accept-invite', { replace: true })
    }
  }, [params, navigate])

  const restart = useCallback(() => {
    setChallenge(null)
    setError('Your sign-in step expired. Use your invitation link again, or ask for a new one.')
  }, [])
  const finish = useCallback((tokens: TokenResponse) => completeSignIn(tokens), [completeSignIn])

  if (state.status === 'authenticated') {
    return <Navigate to="/" replace />
  }

  async function submit(values: AcceptInvitationValues) {
    setError(null)
    try {
      setChallenge(await authApi.acceptInvitation(inviteToken ?? '', values.fullName, values.password))
    } catch (problem) {
      setError(describeAuthError(problem))
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 p-4">
      <Card className="flex w-full max-w-sm flex-col gap-4 p-6">
        <h1 className="text-xl font-semibold text-slate-900">Welcome to Nexlyn BGV</h1>
        {!inviteToken && (
          <>
            <Alert variant="error">This page needs the link from your invitation. Open the link again.</Alert>
            <Link className="text-sm text-slate-600 underline" to="/login">
              Go to sign in
            </Link>
          </>
        )}
        {inviteToken && !challenge && (
          <form className="flex flex-col gap-3" onSubmit={form.handleSubmit(submit)} noValidate>
            <p className="text-sm text-slate-600">Choose your name and a password. You will set up two-factor sign-in next.</p>
            {error && <Alert variant="error">{error}</Alert>}
            <Field label="Full name" htmlFor="invite-name" error={form.formState.errors.fullName?.message}>
              <Input id="invite-name" autoComplete="name" aria-invalid={!!form.formState.errors.fullName} {...form.register('fullName')} />
            </Field>
            <Field
              label="Password"
              htmlFor="invite-password"
              hint="At least 12 characters, mixing at least 3 of: lowercase, uppercase, digits, symbols."
              error={form.formState.errors.password?.message}
            >
              <Input
                id="invite-password"
                type="password"
                autoComplete="new-password"
                aria-invalid={!!form.formState.errors.password}
                {...form.register('password')}
              />
            </Field>
            <Field label="Confirm password" htmlFor="invite-confirm" error={form.formState.errors.confirmPassword?.message}>
              <Input
                id="invite-confirm"
                type="password"
                autoComplete="new-password"
                aria-invalid={!!form.formState.errors.confirmPassword}
                {...form.register('confirmPassword')}
              />
            </Field>
            <Button type="submit" disabled={form.formState.isSubmitting}>
              Continue
            </Button>
          </form>
        )}
        {challenge && <TwoFactorSetup challengeToken={challenge.challengeToken} onFinished={finish} onExpired={restart} />}
      </Card>
    </div>
  )
}
