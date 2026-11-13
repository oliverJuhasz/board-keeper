import { z } from "zod"
import type {
  Game,
  GameModule,
  NewPlay,
  Play,
  PlayPreviewRequest,
  ScoreCategory,
  ScorePad,
} from "@/api/client"
import { GENERIC } from "@/features/games/useGameModules"
import { todayIso } from "@/lib/dates"

// The rules of the form for a Play, and what it sends. The form is drawn from the score pad
// of the Game's Game Module, so everything here takes the module as its first argument.
//
// One module is special. The Generic Module scores any Game that has no module of its own, and
// its "cooperative" Variant turns a Play into one that is won or lost by the group. A score
// pad cannot say that, so it is written out here.

/** A whole number as typed, which may be negative. */
const WHOLE_NUMBER = /^-?\d{1,9}$/

/**
 * Everything the form holds. Some of it only counts for one way of recording: the scores for a
 * competitive Play in points, `won` when only the winner is recorded, and the `group` fields
 * for a cooperative Play. What doesn't count is neither checked nor sent.
 */
const fields = z.object({
  playedOn: z.string().min(1, "validation.required"),
  location: z.string().trim().max(100, "validation.locationTooLong"),
  notes: z.string().trim().max(2000, "validation.notesTooLong"),
  recordingMode: z.enum(["POINTS", "WINNER_ONLY"]),
  /** The Setup: what was chosen for each Variant on the pad, by the Variant's key. */
  setup: z.record(z.string(), z.string()),
  players: z.array(
    z.object({
      playerId: z.string(),
      /** What was typed in each row of the pad, by the Score Category's key. */
      scores: z.record(z.string(), z.string().trim()),
      won: z.boolean(),
    }),
  ),
  /**
   * The recorder's answer to "who won the tie": a Player's id, or SHARED. Empty until they
   * have answered.
   */
  tieWinner: z.string(),
  groupOutcome: z.enum(["", "won", "lost"]),
  groupScore: z.string().trim(),
})

export type PlayFormValues = z.infer<typeof fields>

/** The answer "nobody: the win is shared" to the question who won a tie. */
export const SHARED = "shared"

/**
 * The checks for a Play of this module's Game. Each message is the name of a text under
 * "validation" in the translation files.
 */
export function playFormFor(module: GameModule) {
  // superRefine adds rules that involve more than one field. Each problem is attached to the
  // field it should be shown at, through `path`.
  return fields.superRefine((play, context) => {
    // Dates in this form sort like text, so they can be compared like text.
    if (play.playedOn > todayIso()) {
      context.addIssue({ code: "custom", path: ["playedOn"], message: "validation.dateInFuture" })
    }
    for (const problem of entryProblems(module, play)) {
      context.addIssue({ code: "custom", ...problem })
    }
  })
}

/** Whether the Play is won or lost by the group. Only the Generic Module has such Plays. */
export function isCooperative(module: GameModule, play: Pick<PlayFormValues, "setup">) {
  return module.key === GENERIC && play.setup.cooperative === "true"
}

/** Whether the Players' scores are entered: in a competitive Play recorded in points. */
export function entersScores(
  module: GameModule,
  play: Pick<PlayFormValues, "setup" | "recordingMode">,
) {
  return !isCooperative(module, play) && play.recordingMode === "POINTS"
}

/** What the Setup holds for a Variant: what was chosen, or else the Variant's default. */
function chosen(pad: ScorePad, setup: Record<string, string>, variantKey: string) {
  return (
    setup[variantKey] ?? pad.variants.find((variant) => variant.key === variantKey)?.defaultValue
  )
}

/** The rows of the score pad for a Play with this Setup. A Variant can take a row off the pad. */
export function categoriesOnPad(pad: ScorePad, setup: Record<string, string>) {
  return pad.categories.filter(
    (category) =>
      !category.onlyWhen ||
      chosen(pad, setup, category.onlyWhen.variant) === category.onlyWhen.value,
  )
}

/** The fewest Players a Play with these choices can have. */
export function fewestPlayers(module: GameModule, play: Pick<PlayFormValues, "setup">) {
  // With the Generic Module, a Play of one is recorded as cooperative.
  const competitiveGeneric = module.key === GENERIC && !isCooperative(module, play)
  return competitiveGeneric ? 2 : module.scorePad.minPlayers
}

/** What is wrong with a value typed into a row of the pad, if anything. */
function scoreProblem(category: ScoreCategory, typed: string) {
  if (typed === "") {
    return "validation.scoreRequired"
  }
  if (!WHOLE_NUMBER.test(typed)) {
    return "validation.wholeNumber"
  }
  const value = Number(typed)
  if (category.min !== undefined && value < category.min) {
    return "validation.scoreTooLow"
  }
  if (category.max !== undefined && value > category.max) {
    return "validation.scoreTooHigh"
  }
  return value % category.step === 0 ? undefined : "validation.scoreStep"
}

/**
 * Everything that is wrong with what was entered about how the Play went, each with the field
 * to show it at. An empty list means the Game Module can score it.
 */
export function entryProblems(module: GameModule, play: PlayFormValues) {
  const problems: { path: (string | number)[]; message: string }[] = []
  const pad = module.scorePad
  const points = play.recordingMode === "POINTS"

  const fewest = fewestPlayers(module, play)
  if (play.players.length < fewest) {
    problems.push({
      path: ["players"],
      message: fewest === 1 ? "validation.playerRequired" : "validation.playersTooFew",
    })
  } else if (pad.maxPlayers !== undefined && play.players.length > pad.maxPlayers) {
    problems.push({ path: ["players"], message: "validation.playersTooMany" })
  }

  if (isCooperative(module, play)) {
    if (play.groupOutcome === "") {
      problems.push({ path: ["groupOutcome"], message: "validation.outcomeRequired" })
    }
    if (points && play.groupScore !== "" && !WHOLE_NUMBER.test(play.groupScore)) {
      problems.push({ path: ["groupScore"], message: "validation.wholeNumber" })
    }
    return problems
  }

  if (!points) {
    if (play.players.length >= fewest && !play.players.some((player) => player.won)) {
      problems.push({ path: ["players"], message: "validation.winnerRequired" })
    }
    return problems
  }

  const categories = categoriesOnPad(pad, play.setup)
  play.players.forEach((player, index) => {
    for (const category of categories) {
      const message = scoreProblem(category, (player.scores[category.key] ?? "").trim())
      if (message) {
        problems.push({ path: ["players", index, "scores", category.key], message })
      }
    }
  })
  return problems
}

/** A row for a Player who was just added to the Play, with every row of the pad still empty. */
export function newPlayerRow(pad: ScorePad, playerId: string) {
  return {
    playerId,
    scores: Object.fromEntries(pad.categories.map((category) => [category.key, ""])),
    won: false,
  }
}

/**
 * What was entered about how the Play went, in the form the API takes it.
 *
 * @param tie the Players of a tie for first place that the recorder may decide, as the server
 *     worked it out. The recorder's answer only counts if it names one of them.
 */
function entryOf(module: GameModule, play: PlayFormValues, tie: string[]) {
  const cooperative = isCooperative(module, play)
  const points = play.recordingMode === "POINTS"
  const categories = entersScores(module, play) ? categoriesOnPad(module.scorePad, play.setup) : []

  // Every Variant on the pad is sent, chosen or not, so a stored Setup says all there is.
  const setup = Object.fromEntries(
    module.scorePad.variants.map((variant) => [
      variant.key,
      chosen(module.scorePad, play.setup, variant.key) ?? variant.defaultValue,
    ]),
  )
  if (module.key === GENERIC && !entersScores(module, play)) {
    // "The lowest score wins" means nothing without scores to compare.
    setup.lowestScoreWins = "false"
  }

  function isMarkedWinner(player: PlayFormValues["players"][number]) {
    if (cooperative) {
      return false
    }
    // In points, a winner is only marked to decide a tie for first place.
    return points ? tie.includes(player.playerId) && play.tieWinner === player.playerId : player.won
  }

  return {
    recordingMode: play.recordingMode,
    setup,
    players: play.players.map((player) => ({
      playerId: player.playerId,
      scores:
        categories.length > 0
          ? Object.fromEntries(
              categories.map((category) => [category.key, Number(player.scores[category.key])]),
            )
          : undefined,
      winner: isMarkedWinner(player),
    })),
    group: cooperative
      ? {
          won: play.groupOutcome === "won",
          scores: points && play.groupScore !== "" ? { total: Number(play.groupScore) } : undefined,
        }
      : undefined,
  }
}

/** What the API is sent to record or change a Play, for a form that passed its checks. */
export function toNewPlay(
  module: GameModule,
  gameId: string,
  play: PlayFormValues,
  tie: string[] = [],
): NewPlay {
  return {
    gameId,
    playedOn: play.playedOn,
    location: play.location || undefined,
    notes: play.notes || undefined,
    ...entryOf(module, play, tie),
  }
}

/**
 * What the API is sent to work out how the Play would end. It never marks a winner: the answer
 * says whether there is a tie to decide in the first place.
 */
export function toPreviewRequest(module: GameModule, play: PlayFormValues): PlayPreviewRequest {
  return { gameModuleId: module.id, ...entryOf(module, play, []) }
}

/**
 * The form as it starts out for a new Play of a Game: each Variant at its default, today's
 * date, and one Player already put in if `firstPlayerId` is given. A Game without a module of
 * its own brings its own recording choices.
 */
export function blankPlay(module: GameModule, game: Game, firstPlayerId?: string): PlayFormValues {
  const setup = Object.fromEntries(
    module.scorePad.variants.map((variant) => [variant.key, variant.defaultValue]),
  )
  if (module.key === GENERIC) {
    setup.cooperative = String(game.cooperative)
    setup.lowestScoreWins = String(game.lowestScoreWins)
  }
  return {
    playedOn: todayIso(),
    location: "",
    notes: "",
    recordingMode: module.key === GENERIC ? game.recordingMode : "POINTS",
    setup,
    players: firstPlayerId ? [newPlayerRow(module.scorePad, firstPlayerId)] : [],
    tieWinner: "",
    groupOutcome: "",
    groupScore: "",
  }
}

/**
 * The form filled in with a recorded Play, for editing it. The opposite of toNewPlay.
 *
 * @param module the Game Module that scored the Play, which is not always the one that scores
 *     its Game today
 */
export function fromPlay(module: GameModule, play: Play): PlayFormValues {
  const markedWinners = play.results.filter((result) => result.markedWinner)
  return {
    playedOn: play.playedOn,
    location: play.location ?? "",
    notes: play.notes ?? "",
    recordingMode: play.recordingMode,
    setup: Object.fromEntries(
      module.scorePad.variants.map((variant) => [
        variant.key,
        play.setup[variant.key] ?? variant.defaultValue,
      ]),
    ),
    players: play.results.map((result) => ({
      playerId: result.player.id,
      scores: Object.fromEntries(
        module.scorePad.categories.map((category) => [
          category.key,
          result.scores[category.key]?.toString() ?? "",
        ]),
      ),
      won: result.markedWinner,
    })),
    // What the recorder answered when the Play was recorded: one Player, or else a shared win.
    // The form can give a tie to one Player; a Play recorded through the API with several
    // marked winners shows as shared here.
    tieWinner: markedWinners.length === 1 ? markedWinners[0].player.id : SHARED,
    groupOutcome: play.group ? (play.group.won ? "won" : "lost") : "",
    groupScore: play.group?.scores.total?.toString() ?? "",
  }
}
