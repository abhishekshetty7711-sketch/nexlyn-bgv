import { type ReactNode, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { configureAuthHooks } from '@/api/httpClient'
import { AuthContext, type AuthContextValue, type AuthState, type SessionEnd } from './AuthContext'
import { authApi } from './authApi'
import { listenToOtherTabs, tellOtherTabs } from './authChannel'
import { refreshSession } from './session'
import { tokenStore } from './tokenStore'
import type { TokenResponse } from './types'
import { useIdleLogout } from './useIdleLogout'

/** Renew the access token this many seconds before it expires. */
const REFRESH_MARGIN_SECONDS = 60

/**
 * Owns the signed-in state (CLAUDE.md §11.5): keeps the access token in memory, renews it quietly
 * before it expires, restores the session after a page reload from the refresh cookie, signs out on
 * idle, and signs out every tab together.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [state, setState] = useState<AuthState>({ status: 'loading' })
  const refreshTimer = useRef<number | undefined>(undefined)

  const clearLocalSession = useCallback(
    (endedBy: SessionEnd | null) => {
      window.clearTimeout(refreshTimer.current)
      tokenStore.set(null)
      queryClient.clear() // nothing from the old session (names, PII) may stay in memory
      setState({ status: 'anonymous', endedBy })
    },
    [queryClient],
  )

  const scheduleRefresh = useCallback(
    (expiresInSeconds: number) => {
      // A named inner function, so the timer can schedule the next renewal after each success.
      function schedule(seconds: number) {
        window.clearTimeout(refreshTimer.current)
        const delaySeconds = Math.max(seconds - REFRESH_MARGIN_SECONDS, 10)
        refreshTimer.current = window.setTimeout(async () => {
          const tokens = await refreshSession()
          if (tokens) {
            schedule(tokens.expiresInSeconds)
          } else {
            clearLocalSession('expired')
          }
        }, delaySeconds * 1000)
      }
      schedule(expiresInSeconds)
    },
    [clearLocalSession],
  )

  const completeSignIn = useCallback(
    async (tokens: TokenResponse) => {
      tokenStore.set(tokens.accessToken)
      scheduleRefresh(tokens.expiresInSeconds)
      const me = await authApi.fetchMe()
      setState({ status: 'authenticated', me })
    },
    [scheduleRefresh],
  )

  /** Ends the session on the server (best effort), tells the other tabs, and clears this one. */
  const endSession = useCallback(
    async (reason: Exclude<SessionEnd, 'expired'>) => {
      try {
        await authApi.logout()
      } catch {
        // The server may be unreachable or the session already gone: signing out locally still applies.
      }
      tellOtherTabs({ type: 'logout' })
      clearLocalSession(reason)
    },
    [clearLocalSession],
  )

  // On load: restore the session from the refresh cookie, if there is one.
  useEffect(() => {
    let cancelled = false
    void (async () => {
      const tokens = await refreshSession()
      if (cancelled) {
        return
      }
      if (!tokens) {
        setState({ status: 'anonymous', endedBy: null })
        return
      }
      scheduleRefresh(tokens.expiresInSeconds)
      try {
        const me = await authApi.fetchMe()
        if (!cancelled) {
          setState({ status: 'authenticated', me })
        }
      } catch {
        if (!cancelled) {
          clearLocalSession(null)
        }
      }
    })()
    return () => {
      cancelled = true
      window.clearTimeout(refreshTimer.current)
    }
  }, [scheduleRefresh, clearLocalSession])

  // Let the API client renew an expired token once, and tell us when the session is really over.
  useEffect(() => {
    configureAuthHooks({
      refresh: async () => {
        const tokens = await refreshSession()
        if (tokens) {
          scheduleRefresh(tokens.expiresInSeconds)
        }
        return tokens?.accessToken ?? null
      },
      onSessionEnded: () => clearLocalSession('expired'),
    })
    return () => configureAuthHooks(null)
  }, [scheduleRefresh, clearLocalSession])

  // Another tab signed out: so do we (the server session is already gone).
  useEffect(
    () =>
      listenToOtherTabs((message) => {
        if (message.type === 'logout') {
          clearLocalSession('signed-out')
        }
      }),
    [clearLocalSession],
  )

  const { secondsLeft, keepAlive } = useIdleLogout(
    state.status === 'authenticated',
    useCallback(() => void endSession('idle'), [endSession]),
  )

  const value = useMemo<AuthContextValue>(() => {
    const permissions = new Set(state.status === 'authenticated' ? state.me.permissions : [])
    return {
      state,
      hasPermission: (permission) => permissions.has(permission),
      hasAnyPermission: (wanted) => wanted.some((permission) => permissions.has(permission)),
      completeSignIn,
      signOut: (reason = 'signed-out') => endSession(reason),
      idleSecondsLeft: secondsLeft,
      staySignedIn: keepAlive,
    }
  }, [state, completeSignIn, endSession, secondsLeft, keepAlive])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
