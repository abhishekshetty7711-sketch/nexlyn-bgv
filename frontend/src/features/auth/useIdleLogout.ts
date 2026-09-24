import { useCallback, useEffect, useRef, useState } from 'react'
import { listenToOtherTabs, tellOtherTabs } from './authChannel'

/** CLAUDE.md §11.5: warning at 29 minutes without activity, sign-out at 30. */
export const IDLE_LIMIT_MS = 30 * 60 * 1000
export const IDLE_WARNING_MS = 60 * 1000

const ACTIVITY_EVENTS = ['mousemove', 'mousedown', 'keydown', 'scroll', 'touchstart'] as const
const TELL_OTHER_TABS_EVERY_MS = 15_000

/**
 * Tracks user activity while `active`. Returns the seconds left once the warning period has started
 * (null before that) and a `keepAlive` to dismiss the warning. Calls `onIdle` at the limit.
 * Activity in any tab counts for all tabs.
 */
export function useIdleLogout(active: boolean, onIdle: () => void) {
  const [secondsLeft, setSecondsLeft] = useState<number | null>(null)
  const lastActivity = useRef(0) // set when the timer starts (an effect), not during render
  const warning = useRef(false)
  const lastBroadcast = useRef(0)
  const onIdleRef = useRef(onIdle)

  useEffect(() => {
    onIdleRef.current = onIdle
  }, [onIdle])

  const markActive = useCallback(() => {
    lastActivity.current = Date.now()
    warning.current = false
    setSecondsLeft(null)
    if (Date.now() - lastBroadcast.current > TELL_OTHER_TABS_EVERY_MS) {
      lastBroadcast.current = Date.now()
      tellOtherTabs({ type: 'activity' })
    }
  }, [])

  useEffect(() => {
    if (!active) {
      return
    }
    lastActivity.current = Date.now()
    warning.current = false

    // Once the warning is showing, only the explicit "stay signed in" button counts, so a nudge of
    // the mouse does not silently cancel it.
    const onActivity = () => {
      if (!warning.current) {
        markActive()
      }
    }
    ACTIVITY_EVENTS.forEach((name) => window.addEventListener(name, onActivity, { passive: true }))
    const stopListening = listenToOtherTabs((message) => {
      if (message.type === 'activity' && !warning.current) {
        lastActivity.current = Date.now()
      }
    })

    const timer = window.setInterval(() => {
      const idleFor = Date.now() - lastActivity.current
      if (idleFor >= IDLE_LIMIT_MS) {
        window.clearInterval(timer)
        onIdleRef.current()
      } else if (idleFor >= IDLE_LIMIT_MS - IDLE_WARNING_MS) {
        warning.current = true
        setSecondsLeft(Math.ceil((IDLE_LIMIT_MS - idleFor) / 1000))
      }
    }, 1000)

    return () => {
      window.clearInterval(timer)
      stopListening()
      ACTIVITY_EVENTS.forEach((name) => window.removeEventListener(name, onActivity))
      warning.current = false
      setSecondsLeft(null)
    }
  }, [active, markActive])

  return { secondsLeft, keepAlive: markActive }
}
