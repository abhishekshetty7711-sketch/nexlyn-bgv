import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'
import { configureAuthHooks } from '@/api/httpClient'
import { tokenStore } from '@/features/auth/tokenStore'

// Every test starts from a clean slate: no leftover fake fetch, timers, token, cookie or hooks.
afterEach(() => {
  cleanup()
  vi.useRealTimers()
  vi.unstubAllGlobals()
  configureAuthHooks(null)
  tokenStore.set(null)
  document.cookie = 'csrf_token=; Max-Age=0; path=/'
})
