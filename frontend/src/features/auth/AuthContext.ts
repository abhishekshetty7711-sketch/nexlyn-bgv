import { createContext, useContext } from 'react'
import type { Me, TokenResponse } from './types'

/** Why the session ended, so the login page can explain it. */
export type SessionEnd = 'idle' | 'expired' | 'signed-out' | 'password-changed'

export type AuthState =
  | { status: 'loading' }
  | { status: 'anonymous'; endedBy: SessionEnd | null }
  | { status: 'authenticated'; me: Me }

export interface AuthContextValue {
  state: AuthState
  /** UX only: the server checks every permission again. */
  hasPermission: (permission: string) => boolean
  hasAnyPermission: (permissions: readonly string[]) => boolean
  /** Call with the tokens of a finished login: stores the access token and loads the admin. */
  completeSignIn: (tokens: TokenResponse) => Promise<void>
  signOut: (reason?: 'signed-out' | 'password-changed') => Promise<void>
  /** Seconds until automatic sign-out, once the idle warning has started; otherwise null. */
  idleSecondsLeft: number | null
  staySignedIn: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext)
  if (!value) {
    throw new Error('useAuth must be used inside <AuthProvider>')
  }
  return value
}
