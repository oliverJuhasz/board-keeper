import { useMemo, useSyncExternalStore } from "react"
import type { NewPlay } from "@/api/client"

/**
 * A Play recorded on this device that hasn't reached Board Keeper yet: recorded without a
 * connection, or when the server couldn't be reached. It is sent as soon as it can be
 * (useSendWaitingPlays), under the id chosen when it was recorded, so sending it twice records
 * it once.
 */
export type WaitingPlay = {
  /** The Account that recorded it. It is sent only while that Account is signed in. */
  accountId: string
  /** The Game Module that drew the form, for asking the server how the Play ends. */
  gameModuleId: string
  /** What the API is sent, with the Play's id. */
  play: NewPlay & { id: string }
  /** When it was recorded on the device. */
  savedAt: string
  /**
   * Why it can't be sent as it is, until the recorder has looked at it: a code under "problems"
   * in the translation files, with the Game Module's reason if there is one.
   */
  problem?: { code: string; reason?: string }
}

// The Plays are kept in the browser's localStorage, which survives closing the app. It holds
// text only, so they are stored as JSON.
const KEY = "boardkeeper.waitingPlays"
const listeners = new Set<() => void>()
let cached: { stored: string | null; plays: WaitingPlay[] } = { stored: null, plays: [] }

/**
 * All waiting Plays, from any Account. useSyncExternalStore asks for this on every drawing and
 * expects the very same array while nothing changed, so it is read again only when the stored
 * text differs.
 */
function allWaiting(): WaitingPlay[] {
  const stored = localStorage.getItem(KEY)
  if (stored !== cached.stored) {
    let plays: WaitingPlay[] = []
    try {
      plays = stored ? (JSON.parse(stored) as WaitingPlay[]) : []
    } catch {
      // Unreadable: start afresh rather than break the app.
    }
    cached = { stored, plays }
  }
  return cached.plays
}

function store(plays: WaitingPlay[]) {
  localStorage.setItem(KEY, JSON.stringify(plays))
  listeners.forEach((changed) => changed())
}

function subscribe(changed: () => void) {
  listeners.add(changed)
  // Another tab of the app changed them.
  const fromAnotherTab = (event: StorageEvent) => event.key === KEY && changed()
  window.addEventListener("storage", fromAnotherTab)
  return () => {
    listeners.delete(changed)
    window.removeEventListener("storage", fromAnotherTab)
  }
}

/** Keeps a Play on the device until it can be sent, in place of an earlier copy of it. */
export function keepWaiting(waiting: WaitingPlay) {
  store([...allWaiting().filter((other) => other.play.id !== waiting.play.id), waiting])
}

/** Forgets a waiting Play: it was sent, or its recorder threw it away. */
export function forgetWaiting(playId: string) {
  store(allWaiting().filter((waiting) => waiting.play.id !== playId))
}

/** Marks a waiting Play as needing its recorder before it can be sent. */
export function needsAttention(playId: string, problem: WaitingPlay["problem"]) {
  store(
    allWaiting().map((waiting) => (waiting.play.id === playId ? { ...waiting, problem } : waiting)),
  )
}

/** The Plays this Account recorded on this device that are still waiting, oldest first. */
export function useWaitingPlays(accountId: string) {
  const all = useSyncExternalStore(subscribe, allWaiting)
  return useMemo(() => all.filter((waiting) => waiting.accountId === accountId), [all, accountId])
}

/** One waiting Play of this Account, by its id. */
export function useWaitingPlay(accountId: string, playId: string | null) {
  return useWaitingPlays(accountId).find((waiting) => waiting.play.id === playId)
}
