import { useQueryClient } from "@tanstack/react-query"
import { useEffect, useRef, useState } from "react"
import { fetchClient, type Problem } from "@/api/client"
import { useCurrentAccount } from "@/features/session/useSession"
import { useOnline } from "@/lib/online"
import { forgetWaiting, needsAttention, useWaitingPlays, type WaitingPlay } from "./waitingPlays"

/** How often to try again while Plays wait, for when the device is online but the server isn't. */
const TRY_AGAIN_EVERY = 60 * 1000

/** How sending one waiting Play went. */
type Outcome = "sent" | "needsAttention" | "unreachable"

/**
 * Sends the Plays that wait on this device for the signed-in Account (see waitingPlays.ts): when
 * the app starts, when the device comes back online, when a Play is added, and every minute while
 * any wait. One at a time, oldest first; when the server can't be reached, the rest wait for the
 * next try.
 */
export function useSendWaitingPlays() {
  const account = useCurrentAccount()
  const waiting = useWaitingPlays(account.id)
  const online = useOnline()
  const queryClient = useQueryClient()
  // A ref, not state: whether a round is under way must not redraw anything.
  const sending = useRef(false)
  const [round, setRound] = useState(0)
  const ready = waiting.filter((play) => !play.problem)
  // The effect below runs again only when this text changes: not on every drawing, and not
  // straight after a round that couldn't reach the server, which would try again at once.
  const readyIds = ready.map((play) => play.play.id).join()

  useEffect(() => {
    if (!online || readyIds === "" || sending.current) {
      return
    }
    sending.current = true
    void (async () => {
      let sentAny = false
      try {
        for (const play of ready) {
          const outcome = await send(play)
          sentAny ||= outcome === "sent"
          if (outcome === "unreachable") {
            break
          }
        }
      } finally {
        sending.current = false
        if (sentAny) {
          // The history, the start page and the statistics include them now.
          void queryClient.invalidateQueries({
            predicate: (query) => {
              const address = query.queryKey[1]
              return (
                typeof address === "string" &&
                (address.startsWith("/api/plays") || address.startsWith("/api/stats"))
              )
            },
          })
        }
      }
    })()
    // `ready` is left out on purpose: `readyIds` says when it changed.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [online, readyIds, round, queryClient])

  useEffect(() => {
    if (readyIds === "") {
      return
    }
    const timer = setInterval(() => setRound((current) => current + 1), TRY_AGAIN_EVERY)
    return () => clearInterval(timer)
  }, [readyIds])
}

async function send(waiting: WaitingPlay): Promise<Outcome> {
  const { play } = waiting
  try {
    // Without a connection, nobody was asked who won a tie. If the scores leave one that only
    // the recorder can decide, ask them now, instead of recording a shared win they never chose.
    if (couldTieUndecided(play)) {
      const preview = await fetchClient.POST("/api/plays/preview", {
        body: {
          gameModuleId: waiting.gameModuleId,
          recordingMode: play.recordingMode,
          setup: play.setup,
          players: play.players,
          group: play.group,
        },
      })
      if (preview.error) {
        return refused(waiting, preview.response, preview.error)
      }
      if (preview.data.tieForRecorder.length > 0) {
        needsAttention(play.id, { code: "plays.tieUndecided" })
        return "needsAttention"
      }
    }
    const recorded = await fetchClient.POST("/api/plays", { body: play })
    if (recorded.error) {
      return refused(waiting, recorded.response, recorded.error)
    }
    forgetWaiting(play.id)
    return "sent"
  } catch {
    // fetch() fails without an answer when the server can't be reached.
    return "unreachable"
  }
}

/** A Play in points of several Players, nobody marked as winner, may hide a tie to decide. */
function couldTieUndecided(play: WaitingPlay["play"]) {
  return (
    play.recordingMode === "POINTS" &&
    !play.group &&
    play.players.length > 1 &&
    !play.players.some((player) => player.winner)
  )
}

/**
 * What a failed answer means for the waiting Play. A refusal (4xx) would be the same next time,
 * so the recorder has to look at it. "Not signed in", too many requests and server failures
 * may pass: the Play waits for the next try.
 */
function refused(waiting: WaitingPlay, response: Response, problem: Problem): Outcome {
  const passing = [401, 408, 429].includes(response.status) || response.status >= 500
  if (passing) {
    return "unreachable"
  }
  needsAttention(waiting.play.id, { code: problem.code ?? "unknown", reason: problem.reason })
  return "needsAttention"
}
