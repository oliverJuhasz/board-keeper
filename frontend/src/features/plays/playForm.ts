import { z } from "zod"
import type { Game, NewPlay, Play } from "@/api/client"
import { todayIso } from "@/lib/dates"

// The form for a Play that the Generic Module scores. It knows that module's Setup (the on/off
// Variants "cooperative" and "lowestScoreWins") and its one Score Category, "total". Games with
// a module of their own get a form described by that module in milestone M3.

/** A score as typed: a whole number, which may be negative. */
const WHOLE_NUMBER = /^-?\d{1,9}$/

/**
 * Everything the form holds. Some of it only counts for one way of recording: `score` for a
 * competitive Play in points, `won` when only the winner is recorded, and the `group` fields
 * for a cooperative Play. What doesn't count is neither checked nor sent.
 *
 * Each message is the name of a text under "validation" in the translation files.
 */
export const playForm = z
  .object({
    playedOn: z.string().min(1, "validation.required"),
    location: z.string().trim().max(100, "validation.locationTooLong"),
    notes: z.string().trim().max(2000, "validation.notesTooLong"),
    cooperative: z.boolean(),
    recordingMode: z.enum(["POINTS", "WINNER_ONLY"]),
    lowestScoreWins: z.boolean(),
    players: z.array(
      z.object({ playerId: z.string(), score: z.string().trim(), won: z.boolean() }),
    ),
    /** The Player the recorder gave a tie for first place to; empty for a shared win. */
    tieWinner: z.string(),
    groupOutcome: z.enum(["", "won", "lost"]),
    groupScore: z.string().trim(),
  })
  // Rules that involve more than one field. Each problem is attached to the field it should
  // be shown at, through `path`.
  .superRefine((play, context) => {
    const problem = (path: (string | number)[], message: string) =>
      context.addIssue({ code: "custom", path, message })
    const points = play.recordingMode === "POINTS"

    // Dates in this form sort like text, so they can be compared like text.
    if (play.playedOn > todayIso()) {
      problem(["playedOn"], "validation.dateInFuture")
    }

    if (play.cooperative) {
      if (play.players.length === 0) {
        problem(["players"], "validation.playerRequired")
      }
      if (play.groupOutcome === "") {
        problem(["groupOutcome"], "validation.outcomeRequired")
      }
      if (points && play.groupScore !== "" && !WHOLE_NUMBER.test(play.groupScore)) {
        problem(["groupScore"], "validation.wholeNumber")
      }
      return
    }

    if (play.players.length < 2) {
      problem(["players"], "validation.playersTooFew")
    } else if (!points && !play.players.some((player) => player.won)) {
      problem(["players"], "validation.winnerRequired")
    }
    if (points) {
      play.players.forEach((player, index) => {
        if (player.score === "") {
          problem(["players", index, "score"], "validation.scoreRequired")
        } else if (!WHOLE_NUMBER.test(player.score)) {
          problem(["players", index, "score"], "validation.wholeNumber")
        }
      })
    }
  })

export type PlayFormValues = z.infer<typeof playForm>

/**
 * The Players who share the best score, once every Player has a score and more than one of
 * them has the best. Only for a competitive Play recorded in points.
 */
export function tiedForFirst(play: Pick<PlayFormValues, "players" | "lowestScoreWins">): string[] {
  const scores = play.players.map((player) => player.score.trim())
  if (scores.length < 2 || !scores.every((score) => WHOLE_NUMBER.test(score))) {
    return []
  }
  const totals = scores.map(Number)
  const best = play.lowestScoreWins ? Math.min(...totals) : Math.max(...totals)
  const tied = play.players.filter((_, index) => totals[index] === best)
  return tied.length > 1 ? tied.map((player) => player.playerId) : []
}

/** What the API is sent for a form that passed its checks. */
export function toNewPlay(gameId: string, play: PlayFormValues): NewPlay {
  const points = play.recordingMode === "POINTS"
  const competitiveInPoints = !play.cooperative && points
  const tied = competitiveInPoints ? tiedForFirst(play) : []

  function isMarkedWinner(player: PlayFormValues["players"][number]) {
    if (play.cooperative) {
      return false
    }
    // In points, a winner is only marked to decide a tie for first place.
    return points
      ? tied.includes(player.playerId) && play.tieWinner === player.playerId
      : player.won
  }

  return {
    gameId,
    playedOn: play.playedOn,
    location: play.location || undefined,
    notes: play.notes || undefined,
    recordingMode: play.recordingMode,
    setup: {
      cooperative: String(play.cooperative),
      lowestScoreWins: String(competitiveInPoints && play.lowestScoreWins),
    },
    players: play.players.map((player) => ({
      playerId: player.playerId,
      scores: competitiveInPoints ? { total: Number(player.score) } : undefined,
      winner: isMarkedWinner(player),
    })),
    group: play.cooperative
      ? {
          won: play.groupOutcome === "won",
          scores: points && play.groupScore !== "" ? { total: Number(play.groupScore) } : undefined,
        }
      : undefined,
  }
}

/**
 * The form as it starts out for a new Play of a Game: the Game's own recording choices, today's
 * date, and one Player already put in if `firstPlayerId` is given.
 */
export function blankPlay(game: Game, firstPlayerId?: string): PlayFormValues {
  return {
    playedOn: todayIso(),
    location: "",
    notes: "",
    cooperative: game.cooperative,
    recordingMode: game.recordingMode,
    lowestScoreWins: game.lowestScoreWins,
    players: firstPlayerId ? [{ playerId: firstPlayerId, score: "", won: false }] : [],
    tieWinner: "",
    groupOutcome: "",
    groupScore: "",
  }
}

/** The form filled in with a recorded Play, for editing it. The opposite of toNewPlay. */
export function fromPlay(play: Play): PlayFormValues {
  const markedWinners = play.results.filter((result) => result.markedWinner)
  return {
    playedOn: play.playedOn,
    location: play.location ?? "",
    notes: play.notes ?? "",
    cooperative: play.cooperative,
    recordingMode: play.recordingMode,
    lowestScoreWins: play.setup.lowestScoreWins === "true",
    players: play.results.map((result) => ({
      playerId: result.player.id,
      score: result.scores.total?.toString() ?? "",
      won: result.markedWinner,
    })),
    // The form can give a tie to one Player. A Play recorded through the API with several
    // marked winners shows as a shared win here.
    tieWinner: markedWinners.length === 1 ? markedWinners[0].player.id : "",
    groupOutcome: play.group ? (play.group.won ? "won" : "lost") : "",
    groupScore: play.group?.scores.total?.toString() ?? "",
  }
}
