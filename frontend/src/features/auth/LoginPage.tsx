import { useCallback, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Navigate, useLocation } from 'react-router-dom'
import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { FullPageSpinner, Spinner } from '@/components/ui/spinner'
import { usePageTitle } from '@/lib/usePageTitle'
import { useAuth } from './AuthContext'
import { authApi } from './authApi'
import { AuthShell } from './AuthShell'
import { describeAuthError, isChallengeExpired } from './errors'
import { type CodeValues, type LoginValues, codeSchema, loginSchema } from './schemas'
import { TwoFactorSetup } from './TwoFactorSetup'
import type { Challenge, TokenResponse } from './types'

const ENDED_MESSAGES = {
  idle: 'You were signed out because you were inactive.',
  expired: 'Your session ended. Please sign in again.',
  'password-changed': 'Your password was changed. Please sign in again.',
  'signed-out': null,
} as const

interface LocationState {
  from?: { pathname: string; search?: string }
}

/** Password, then a second factor: the code from the authenticator app, or first-time setup. */
export function LoginPage() {
  const { state, completeSignIn } = useAuth()
  const location = useLocation()
  const [challenge, setChallenge] = useState<Challenge | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const restart = useCallback(() => {
    setChallenge(null)
    setNotice('Your sign-in step expired. Please sign in again.')
  }, [])

  const finish = useCallback((tokens: TokenResponse) => completeSignIn(tokens), [completeSignIn])

  usePageTitle(
    challenge?.status === '2FA_SETUP_REQUIRED' ? 'Set up two-step verification' : challenge?.status === '2FA_REQUIRED' ? 'Two-step verification' : 'Sign in',
  )

  if (state.status === 'loading') {
    return <FullPageSpinner />
  }
  if (state.status === 'authenticated') {
    const from = (location.state as LocationState | null)?.from
    return <Navigate to={from ? `${from.pathname}${from.search ?? ''}` : '/'} replace />
  }

  const endedMessage = state.endedBy ? ENDED_MESSAGES[state.endedBy] : null
  const subtitle =
    challenge?.status === '2FA_SETUP_REQUIRED'
      ? 'Set up two-step verification'
      : challenge?.status === '2FA_REQUIRED'
        ? 'Two-step verification'
        : 'Sign in to the admin console'

  return (
    <AuthShell subtitle={subtitle}>
      {(notice ?? endedMessage) && <Alert variant="info">{notice ?? endedMessage}</Alert>}
      {!challenge && (
        <CredentialsForm
          onChallenge={(next) => {
            setNotice(null)
            setChallenge(next)
          }}
        />
      )}
      {challenge?.status === '2FA_REQUIRED' && <CodeForm challenge={challenge} onTokens={finish} onExpired={restart} />}
      {challenge?.status === '2FA_SETUP_REQUIRED' && (
        <TwoFactorSetup challengeToken={challenge.challengeToken} onFinished={finish} onExpired={restart} />
      )}
    </AuthShell>
  )
}

function CredentialsForm({ onChallenge }: { onChallenge: (challenge: Challenge) => void }) {
  const [error, setError] = useState<string | null>(null)
  const form = useForm<LoginValues>({ resolver: zodResolver(loginSchema), defaultValues: { email: '', password: '' } })

  async function submit(values: LoginValues) {
    setError(null)
    try {
      onChallenge(await authApi.login(values.email, values.password))
    } catch (problem) {
      setError(describeAuthError(problem))
      form.resetField('password')
    }
  }

  return (
    <form className="flex flex-col gap-4" onSubmit={form.handleSubmit(submit)} noValidate>
      {error && <Alert variant="error">{error}</Alert>}
      <Field label="Email" htmlFor="login-email" error={form.formState.errors.email?.message}>
        <Input
          id="login-email"
          type="email"
          autoComplete="username"
          autoFocus
          aria-invalid={!!form.formState.errors.email}
          {...form.register('email')}
        />
      </Field>
      <Field label="Password" htmlFor="login-password" error={form.formState.errors.password?.message}>
        <Input
          id="login-password"
          type="password"
          autoComplete="current-password"
          aria-invalid={!!form.formState.errors.password}
          {...form.register('password')}
        />
      </Field>
      <Button type="submit" size="lg" disabled={form.formState.isSubmitting}>
        {form.formState.isSubmitting && <Spinner className="h-4 w-4 text-white" />}
        Sign in
      </Button>
    </form>
  )
}

interface CodeFormProps {
  challenge: Challenge
  onTokens: (tokens: TokenResponse) => Promise<void>
  onExpired: () => void
}

function CodeForm({ challenge, onTokens, onExpired }: CodeFormProps) {
  const [error, setError] = useState<string | null>(null)
  const [useBackup, setUseBackup] = useState(false)
  const form = useForm<CodeValues>({ resolver: zodResolver(codeSchema), defaultValues: { code: '' } })

  async function submit(values: CodeValues) {
    setError(null)
    try {
      await onTokens(await authApi.verifyTwoFactor(challenge.challengeToken, values.code))
    } catch (problem) {
      if (isChallengeExpired(problem)) {
        onExpired()
        return
      }
      setError(describeAuthError(problem))
      form.reset()
    }
  }

  return (
    <form className="flex flex-col gap-4" onSubmit={form.handleSubmit(submit)} noValidate>
      <p className="text-sm text-slate-600">
        {useBackup
          ? 'Enter one of your backup codes. Each works only once.'
          : 'Enter the 6-digit code from your authenticator app.'}
      </p>
      {error && <Alert variant="error">{error}</Alert>}
      <Field label={useBackup ? 'Backup code' : '6-digit code'} htmlFor="login-code" error={form.formState.errors.code?.message}>
        <Input
          id="login-code"
          inputMode={useBackup ? 'text' : 'numeric'}
          autoComplete="one-time-code"
          autoFocus
          aria-invalid={!!form.formState.errors.code}
          {...form.register('code')}
        />
      </Field>
      <Button type="submit" size="lg" disabled={form.formState.isSubmitting}>
        {form.formState.isSubmitting && <Spinner className="h-4 w-4 text-white" />}
        Verify
      </Button>
      <button
        type="button"
        className="w-fit cursor-pointer text-left text-sm text-brand-700 underline underline-offset-2 hover:text-brand-800"
        onClick={() => {
          setUseBackup((value) => !value)
          form.reset()
        }}
      >
        {useBackup ? 'Use my authenticator app instead' : 'Use a backup code instead'}
      </button>
    </form>
  )
}
