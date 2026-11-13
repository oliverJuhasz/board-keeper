import { useEffect, useState } from "react"
import { api, type GameModule, type PlayPreview, type PlayPreviewRequest } from "@/api/client"
import { entersScores, entryProblems, toPreviewRequest, type PlayFormValues } from "./playForm"

/**
 * How to ask the server how a Play would end. The answer depends on the request alone, so it
 * never goes out of date and the same request is not sent twice.
 */
export function previewOptions(body: PlayPreviewRequest) {
  return api.queryOptions("post", "/api/plays/preview", { body }, { staleTime: Infinity })
}

/** Whether there is anything to work out: scores are entered, and all of them are in. */
export function canBePreviewed(module: GameModule, play: PlayFormValues) {
  return entersScores(module, play) && entryProblems(module, play).length === 0
}

/**
 * The totals and Placings of the Play as it stands in the form, and the tie the recorder has
 * to decide, worked out by the Game Module on the server. Undefined while scores are missing,
 * and for a moment after every change, until the server has answered about the new values.
 *
 * The frontend could add up the rows itself, but only the module knows its Tie-breakers, and
 * asking the server keeps those rules in one place.
 */
export function usePlayPreview(module: GameModule, play: PlayFormValues): PlayPreview | undefined {
  // What to ask about, as text: text can be compared, and waited on, as one value.
  const current = canBePreviewed(module, play)
    ? JSON.stringify(toPreviewRequest(module, play))
    : undefined
  // Wait until the recorder stops typing for a moment, instead of asking at every key. Only
  // what has settled is ever sent, so the server is never asked about a half-typed number.
  const settled = useDebounced(current, 250)
  const preview = api.useQuery(
    "post",
    "/api/plays/preview",
    { body: settled ? (JSON.parse(settled) as PlayPreviewRequest) : nothingToAsk(module) },
    { enabled: settled !== undefined, staleTime: Infinity },
  )
  // An answer about earlier values says nothing about the form as it is now.
  return settled !== undefined && settled === current ? preview.data : undefined
}

/** A request that is never sent: the hook needs one of the right shape while it has nothing to ask. */
function nothingToAsk(module: GameModule): PlayPreviewRequest {
  return { gameModuleId: module.id, recordingMode: "POINTS", setup: {}, players: [] }
}

/** A value that follows the given one, but only once it has stayed the same for a while. */
function useDebounced<Value>(value: Value, milliseconds: number) {
  const [debounced, setDebounced] = useState(value)
  // An effect runs after the component has been drawn. This one starts a timer whenever the
  // value changes, and the function it returns cancels the timer if the value changes again.
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), milliseconds)
    return () => clearTimeout(timer)
  }, [value, milliseconds])
  return debounced
}
