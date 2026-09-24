import { useEffect, useRef, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { QRCodeSVG } from 'qrcode.react'
import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'
import { authApi } from './authApi'
import { describeAuthError, isChallengeExpired } from './errors'
import { type CodeValues, codeSchema } from './schemas'
import type { TokenResponse, TwoFactorSetupInfo } from './types'

interface TwoFactorSetupProps {
  challengeToken: string
  /** Called once the admin has confirmed the app AND acknowledged the backup codes. */
  onFinished: (tokens: TokenResponse) => void
  /** The short-lived sign-in step ran out: the parent should restart from the password. */
  onExpired: () => void
}

/** Groups a Base32 secret in fours so it is easier to type by hand: ABCD EFGH ... */
function groupSecret(secret: string): string {
  return secret.replace(/(.{4})/g, '$1 ').trim()
}

/**
 * First-time two-factor enrolment (mandatory for every admin): scan the QR code (or type the key)
 * into an authenticator app, prove it works with the first code, then save the one-time backup
 * codes. Nothing is signed in until the backup codes have been acknowledged.
 */
export function TwoFactorSetup({ challengeToken, onFinished, onExpired }: TwoFactorSetupProps) {
  const [info, setInfo] = useState<TwoFactorSetupInfo | null>(null)
  const [tokens, setTokens] = useState<TokenResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)
  const [copied, setCopied] = useState(false)
  const started = useRef(false)

  const form = useForm<CodeValues>({ resolver: zodResolver(codeSchema), defaultValues: { code: '' } })

  // Ask the server for a secret exactly once (asking twice would replace the first secret).
  useEffect(() => {
    if (started.current) {
      return
    }
    started.current = true
    authApi
      .setupTwoFactor(challengeToken)
      .then(setInfo)
      .catch((problem: unknown) => {
        if (isChallengeExpired(problem)) {
          onExpired()
        } else {
          setError(describeAuthError(problem))
        }
      })
  }, [challengeToken, onExpired])

  async function confirm(values: CodeValues) {
    setError(null)
    try {
      setTokens(await authApi.confirmTwoFactor(challengeToken, values.code))
    } catch (problem) {
      if (isChallengeExpired(problem)) {
        onExpired()
        return
      }
      setError(describeAuthError(problem))
      form.reset()
    }
  }

  // ---- step 2: backup codes -------------------------------------------------------------
  if (tokens) {
    const codes = tokens.backupCodes ?? []
    const text = codes.join('\n')
    return (
      <div className="flex flex-col gap-4">
        <h2 className="text-lg font-semibold text-slate-900">Save your backup codes</h2>
        <Alert variant="warning">
          These codes let you sign in if you lose your phone. Each works once. They are shown only now and
          cannot be shown again.
        </Alert>
        <ul className="grid grid-cols-2 gap-2 rounded-md border border-slate-200 bg-slate-50 p-3 font-mono text-sm" aria-label="Backup codes">
          {codes.map((code) => (
            <li key={code}>{code}</li>
          ))}
        </ul>
        <div className="flex gap-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => {
              void navigator.clipboard?.writeText(text).then(() => setCopied(true))
            }}
          >
            {copied ? 'Copied' : 'Copy codes'}
          </Button>
          <a
            className="inline-flex h-8 items-center rounded-md border border-slate-300 px-3 text-xs font-medium hover:bg-slate-100"
            href={`data:text/plain;charset=utf-8,${encodeURIComponent(text)}`}
            download="nexlyn-backup-codes.txt"
          >
            Download
          </a>
        </div>
        <label className="flex items-start gap-2 text-sm text-slate-700">
          <input type="checkbox" className="mt-1" checked={saved} onChange={(event) => setSaved(event.target.checked)} />
          I have saved these codes somewhere safe.
        </label>
        <Button type="button" disabled={!saved} onClick={() => onFinished(tokens)}>
          Continue
        </Button>
      </div>
    )
  }

  // ---- step 1: scan and confirm ------------------------------------------------------------
  if (!info) {
    return error ? (
      <Alert variant="error">{error}</Alert>
    ) : (
      <div className="flex justify-center p-6" role="status" aria-label="Preparing two-factor setup">
        <Spinner />
      </div>
    )
  }
  return (
    <div className="flex flex-col gap-4">
      <h2 className="text-lg font-semibold text-slate-900">Set up two-factor authentication</h2>
      <p className="text-sm text-slate-600">
        Open an authenticator app (Google Authenticator, Microsoft Authenticator, Authy, 1Password) and scan this code.
      </p>
      <div className="flex justify-center rounded-md border border-slate-200 bg-white p-3">
        <QRCodeSVG value={info.otpauthUri} size={192} level="M" role="img" aria-label="QR code for your authenticator app" />
      </div>
      <p className="text-sm text-slate-600">
        Can&apos;t scan? Enter this key instead: <span className="font-mono font-medium">{groupSecret(info.secret)}</span>
      </p>
      {error && <Alert variant="error">{error}</Alert>}
      <form className="flex flex-col gap-3" onSubmit={form.handleSubmit(confirm)} noValidate>
        <Field label="6-digit code" htmlFor="setup-code" error={form.formState.errors.code?.message}>
          <Input
            id="setup-code"
            inputMode="numeric"
            autoComplete="one-time-code"
            aria-invalid={!!form.formState.errors.code}
            {...form.register('code')}
          />
        </Field>
        <Button type="submit" disabled={form.formState.isSubmitting}>
          Verify and continue
        </Button>
      </form>
    </div>
  )
}
