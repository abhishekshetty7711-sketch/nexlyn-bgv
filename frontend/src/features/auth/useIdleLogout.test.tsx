import { act, renderHook } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { IDLE_LIMIT_MS, IDLE_WARNING_MS, useIdleLogout } from './useIdleLogout'

const MINUTE = 60_000

function setup(active = true) {
  vi.useFakeTimers()
  const onIdle = vi.fn()
  const hook = renderHook(({ on }) => useIdleLogout(on, onIdle), { initialProps: { on: active } })
  return { onIdle, hook }
}

describe('useIdleLogout', () => {
  it('uses 30 minutes as the limit and warns 60 seconds before', () => {
    expect(IDLE_LIMIT_MS).toBe(30 * MINUTE)
    expect(IDLE_WARNING_MS).toBe(MINUTE)
  })

  it('warns at 29 minutes and signs out at 30', () => {
    const { onIdle, hook } = setup()

    act(() => void vi.advanceTimersByTime(28 * MINUTE + 59_000))
    expect(hook.result.current.secondsLeft).toBeNull()

    act(() => void vi.advanceTimersByTime(2_000)) // 29:01 idle
    expect(hook.result.current.secondsLeft).toBe(59)
    expect(onIdle).not.toHaveBeenCalled()

    act(() => void vi.advanceTimersByTime(MINUTE))
    expect(onIdle).toHaveBeenCalledTimes(1)
  })

  it('counts the warning down second by second', () => {
    const { hook } = setup()
    act(() => void vi.advanceTimersByTime(29 * MINUTE + 1_000))
    const first = hook.result.current.secondsLeft
    act(() => void vi.advanceTimersByTime(10_000))
    expect(first! - hook.result.current.secondsLeft!).toBe(10)
  })

  it('treats activity before the warning as proof the user is there', () => {
    const { onIdle, hook } = setup()

    act(() => void vi.advanceTimersByTime(20 * MINUTE))
    act(() => void window.dispatchEvent(new Event('keydown')))
    act(() => void vi.advanceTimersByTime(20 * MINUTE)) // 40 minutes in total, but only 20 since the keypress

    expect(onIdle).not.toHaveBeenCalled()
    expect(hook.result.current.secondsLeft).toBeNull()
  })

  it('does not let a stray mouse movement silently cancel the warning', () => {
    const { onIdle, hook } = setup()
    act(() => void vi.advanceTimersByTime(29 * MINUTE + 10_000))
    expect(hook.result.current.secondsLeft).not.toBeNull()

    act(() => void window.dispatchEvent(new Event('mousemove')))
    expect(hook.result.current.secondsLeft).not.toBeNull()

    act(() => void vi.advanceTimersByTime(MINUTE))
    expect(onIdle).toHaveBeenCalledTimes(1)
  })

  it('lets "stay signed in" dismiss the warning and start a fresh 30 minutes', () => {
    const { onIdle, hook } = setup()
    act(() => void vi.advanceTimersByTime(29 * MINUTE + 30_000))
    expect(hook.result.current.secondsLeft).not.toBeNull()

    act(() => hook.result.current.keepAlive())
    expect(hook.result.current.secondsLeft).toBeNull()

    act(() => void vi.advanceTimersByTime(29 * MINUTE))
    expect(onIdle).not.toHaveBeenCalled()
    act(() => void vi.advanceTimersByTime(2 * MINUTE))
    expect(onIdle).toHaveBeenCalledTimes(1)
  })

  it('does nothing while signed out', () => {
    const { onIdle, hook } = setup(false)
    act(() => void vi.advanceTimersByTime(2 * 60 * MINUTE))
    expect(onIdle).not.toHaveBeenCalled()
    expect(hook.result.current.secondsLeft).toBeNull()
  })

  it('stops timing when the user signs out, and starts fresh on the next sign-in', () => {
    const { onIdle, hook } = setup()
    act(() => void vi.advanceTimersByTime(25 * MINUTE))
    hook.rerender({ on: false })
    act(() => void vi.advanceTimersByTime(60 * MINUTE))
    expect(onIdle).not.toHaveBeenCalled()

    hook.rerender({ on: true })
    act(() => void vi.advanceTimersByTime(29 * MINUTE))
    expect(onIdle).not.toHaveBeenCalled()
  })
})
