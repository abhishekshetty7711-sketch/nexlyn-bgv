// Tabs of the same browser share one session, so they must share its end too: signing out (or
// being timed out) in one tab signs out all of them, and activity in one tab keeps the others alive.

export type AuthMessage = { type: 'logout' } | { type: 'activity' }

const CHANNEL_NAME = 'nexlyn-bgv-auth'

/** Subscribes to messages from OTHER tabs. Returns an unsubscribe function. No-op where unsupported. */
export function listenToOtherTabs(onMessage: (message: AuthMessage) => void): () => void {
  if (typeof BroadcastChannel === 'undefined') {
    return () => {}
  }
  const channel = new BroadcastChannel(CHANNEL_NAME)
  channel.onmessage = (event: MessageEvent<AuthMessage>) => onMessage(event.data)
  return () => channel.close()
}

/** Tells the other tabs something. A BroadcastChannel never delivers to the tab that sent it. */
export function tellOtherTabs(message: AuthMessage): void {
  if (typeof BroadcastChannel === 'undefined') {
    return
  }
  const channel = new BroadcastChannel(CHANNEL_NAME)
  channel.postMessage(message)
  channel.close()
}
