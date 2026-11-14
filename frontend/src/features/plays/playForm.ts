import { z } from "zod"
import type {
  Game,
  GameModule,
  NewPlay,
  Play,
  PlayPreviewRequest,
  ScoreCategory,
  ScorePad,
  VictoryType,
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
  /**
   * The Setup: what was chosen for each Variant on the pad, and whether each Expansion was
   * played with ("true" or "false"), by their keys.
   */
  setup: z.record(z.string(), z.string()),
  /** How the Play was won: a Victory Type's key. Empty for a Game won in one way only. */
  victoryType: z.string(),
  players: z.array(
    z.object({
      playerId: z.string(),
      /** What was typed in each row of the pad, by the Score Category's key. */
      scores: z.record(z.string(), z.string().trim()),
      won: z.boolean(),
      /**
       * The Player Setup, such as their Wonder: the option chosen for each choice, by the
       * choice's key. Empty for a choice not made.
       */
      setup: z.record(z.string(), z.string()),
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

/**
 * How the Play is played alone, if it is a Solo Play of a Game that can be: for score only, or
 * against an automated opponent. Undefined for a Play of two Players or more. A Play of one with
 * the Generic Module is a cooperative Play instead.
 */
export function soloOf(module: GameModule, play: Pick<PlayFormValues, "players">) {
  return play.players.length === 1 ? module.scorePad.solo : undefined
}

/** The Variants to choose from: those for Solo Plays only are left out of other Plays. */
export function variantsFor(module: GameModule, play: Pick<PlayFormValues, "players">) {
  const solo = soloOf(module, play) !== undefined
  return module.scorePad.variants.filter((variant) => solo || !variant.soloOnly)
}

/**
 * The ways a Play with these choices can be won, the usual one first. Empty for a Game that is
 * won in one way only, and for a Solo Play.
 */
export function victoryTypesFor(
  module: GameModule,
  play: Pick<PlayFormValues, "setup" | "players">,
): VictoryType[] {
  if (soloOf(module, play) !== undefined) {
    return []
  }
  const pad = module.scorePad
  return pad.victoryTypes.filter((victoryType) => holds(pad, play.setup, victoryType.onlyWhen))
}

/**
 * How the Play was won, if the Game can be won in more than one way. A way that the Setup no
 * longer allows, such as political supremacy once Agora is taken off, gives way to the usual one.
 */
export function victoryTypeOf(
  module: GameModule,
  play: Pick<PlayFormValues, "setup" | "players" | "victoryType">,
): VictoryType | undefined {
  const possible = victoryTypesFor(module, play)
  return possible.find((victoryType) => victoryType.key === play.victoryType) ?? possible[0]
}

/**
 * Whether the Play ended outright, such as by a supremacy in 7 Wonders Duel. Nobody counted
 * points then: the recorder ticks the one winner, as when only the winner is recorded.
 */
export function wonOutright(
  module: GameModule,
  play: Pick<PlayFormValues, "setup" | "players" | "victoryType">,
) {
  return victoryTypeOf(module, play)?.onPoints === false
}

/** How the Play is recorded: a Play won outright has only its winner. */
export function recordingModeOf(module: GameModule, play: PlayFormValues) {
  return wonOutright(module, play) ? "WINNER_ONLY" : play.recordingMode
}

/** Whether the Players' scores are entered: in a competitive Play recorded in points. */
export function entersScores(module: GameModule, play: PlayFormValues) {
  return !isCooperative(module, play) && recordingModeOf(module, play) === "POINTS"
}

/**
 * What the Setup holds for a Variant or an Expansion: what was chosen, or else the Variant's
 * default. An Expansion that wasn't chosen wasn't played with.
 */
function chosen(pad: ScorePad, setup: Record<string, string>, key: string) {
  if (pad.expansions.some((expansion) => expansion.key === key)) {
    return setup[key] ?? "false"
  }
  return setup[key] ?? pad.variants.find((variant) => variant.key === key)?.defaultValue
}

/** Whether a Setup meets a condition of the pad. No condition is always met. */
function holds(pad: ScorePad, setup: Record<string, string>, onlyWhen?: ScoreCategory["onlyWhen"]) {
  return !onlyWhen || chosen(pad, setup, onlyWhen.key) === onlyWhen.value
}

/**
 * The Setup to start a form with, or to send: a value for every Expansion and Variant of the
 * pad, taken from `from` where it has one.
 */
function setupFrom(pad: ScorePad, from: Record<string, string>) {
  return Object.fromEntries([
    ...pad.expansions.map((expansion) => [expansion.key, from[expansion.key] ?? "false"]),
    ...pad.variants.map((variant) => [variant.key, from[variant.key] ?? variant.defaultValue]),
  ])
}

/**
 * The rows of the score pad for a Play with this Setup. A Variant can take a row off the pad,
 * and some rows, such as an automated opponent's score, are on the pad of a Solo Play only.
 */
export function categoriesOnPad(pad: ScorePad, setup: Record<string, string>, solo = false) {
  return pad.categories.filter(
    (category) => (solo || !category.soloOnly) && holds(pad, setup, category.onlyWhen),
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
  const points = recordingModeOf(module, play) === "POINTS"

  const fewest = fewestPlayers(module, play)
  if (play.players.length < fewest) {
    problems.push({
      path: ["players"],
      message: fewest === 1 ? "validation.playerRequired" : "validation.playersTooFew",
    })
  } else if (pad.maxPlayers !== undefined && play.players.length > pad.maxPlayers) {
    problems.push({ path: ["players"], message: "validation.playersTooMany" })
  }

  // An option that is there once, such as a Wonder board, can't be given to a second Player.
  for (const choice of pad.playerSetup.filter((choice) => choice.oncePerPlay)) {
    play.players.forEach((player, index) => {
      const option = player.setup[choice.key]
      const before = play.players.slice(0, index)
      if (option && before.some((earlier) => earlier.setup[choice.key] === option)) {
        problems.push({
          path: ["players", index, "setup", choice.key],
          message: "validation.chosenTwice",
        })
      }
    })
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

  const solo = soloOf(module, play)
  if (!points) {
    if (solo?.kind === "FOR_SCORE_ONLY") {
      // Played alone for a score, there is nothing else to record.
      problems.push({ path: ["recordingMode"], message: "validation.soloScoresOnly" })
    } else if (
      !solo &&
      play.players.length >= fewest &&
      !play.players.some((player) => player.won)
    ) {
      // Against an automated opponent, an unticked "won" is a loss. Otherwise someone won.
      problems.push({ path: ["players"], message: "validation.winnerRequired" })
    } else if (
      wonOutright(module, play) &&
      play.players.filter((player) => player.won).length > 1
    ) {
      problems.push({ path: ["players"], message: "validation.oneWinnerOnly" })
    }
    return problems
  }

  const categories = categoriesOnPad(pad, play.setup, solo !== undefined)
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
    setup: Object.fromEntries(pad.playerSetup.map((choice) => [choice.key, ""])),
  }
}

/**
 * A Player's Setup as the form holds it, from one sent or recorded: every choice of the pad,
 * empty where nothing was chosen.
 */
function playerSetupFrom(pad: ScorePad, from: Record<string, string> | undefined) {
  return Object.fromEntries(pad.playerSetup.map((choice) => [choice.key, from?.[choice.key] ?? ""]))
}

/**
 * What was entered about how the Play went, in the form the API takes it.
 *
 * @param tie the Players of a tie for first place that the recorder may decide, as the server
 *     worked it out. The recorder's answer only counts if it names one of them.
 */
function entryOf(module: GameModule, play: PlayFormValues, tie: string[]) {
  const cooperative = isCooperative(module, play)
  const recordingMode = recordingModeOf(module, play)
  const points = recordingMode === "POINTS"
  const solo = soloOf(module, play) !== undefined
  const categories = entersScores(module, play)
    ? categoriesOnPad(module.scorePad, play.setup, solo)
    : []

  // Every Expansion and Variant that applies is sent, chosen or not, so a stored Setup says
  // all there is. Variants for Solo Plays only are left out of other Plays.
  const setup = Object.fromEntries([
    ...module.scorePad.expansions.map((expansion) => [
      expansion.key,
      chosen(module.scorePad, play.setup, expansion.key) ?? "false",
    ]),
    ...variantsFor(module, play).map((variant) => [
      variant.key,
      chosen(module.scorePad, play.setup, variant.key) ?? variant.defaultValue,
    ]),
  ])
  if (module.key === GENERIC && !entersScores(module, play)) {
    // "The lowest score wins" means nothing without scores to compare.
    setup.lowestScoreWins = "false"
  }

  function isMarkedWinner(player: PlayFormValues["players"][number]) {
    if (cooperative) {
      return false
    }
    // In points, a winner is only marked to decide a tie for first place. A Solo Play in points
    // has no tie: the scores say whether it was won.
    if (points) {
      return !solo && tie.includes(player.playerId) && play.tieWinner === player.playerId
    }
    return player.won
  }

  return {
    recordingMode,
    setup,
    victoryType: victoryTypeOf(module, play)?.key,
    players: play.players.map((player) => ({
      playerId: player.playerId,
      scores:
        categories.length > 0
          ? Object.fromEntries(
              categories.map((category) => [category.key, Number(player.scores[category.key])]),
            )
          : undefined,
      winner: isMarkedWinner(player),
      // Only the choices that were made.
      setup: Object.fromEntries(
        module.scorePad.playerSetup
          .filter((choice) => player.setup[choice.key])
          .map((choice) => [choice.key, player.setup[choice.key]]),
      ),
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
  const setup = setupFrom(module.scorePad, {})
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
    // The usual way of winning, such as on points.
    victoryType: module.scorePad.victoryTypes[0]?.key ?? "",
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
    setup: setupFrom(module.scorePad, play.setup),
    victoryType: play.victoryType ?? module.scorePad.victoryTypes[0]?.key ?? "",
    players: play.results.map((result) => ({
      playerId: result.player.id,
      scores: Object.fromEntries(
        module.scorePad.categories.map((category) => [
          category.key,
          result.scores[category.key]?.toString() ?? "",
        ]),
      ),
      won: result.markedWinner,
      setup: playerSetupFrom(module.scorePad, result.setup),
    })),
    // What the recorder answered when the Play was recorded: one Player, or else a shared win.
    // The form can give a tie to one Player; a Play recorded through the API with several
    // marked winners shows as shared here.
    tieWinner: markedWinners.length === 1 ? markedWinners[0].player.id : SHARED,
    groupOutcome: play.group ? (play.group.won ? "won" : "lost") : "",
    groupScore: play.group?.scores.total?.toString() ?? "",
  }
}

/**
 * The form filled in with a Play that waits on this device to be sent (waitingPlays.ts): what
 * toNewPlay made of it, turned back. Nobody was asked about a tie yet, so that is left open.
 */
export function fromNewPlay(module: GameModule, play: NewPlay): PlayFormValues {
  return {
    playedOn: play.playedOn,
    location: play.location ?? "",
    notes: play.notes ?? "",
    recordingMode: play.recordingMode,
    setup: setupFrom(module.scorePad, play.setup),
    victoryType: play.victoryType ?? module.scorePad.victoryTypes[0]?.key ?? "",
    players: play.players.map((player) => ({
      playerId: player.playerId,
      scores: Object.fromEntries(
        module.scorePad.categories.map((category) => [
          category.key,
          player.scores?.[category.key]?.toString() ?? "",
        ]),
      ),
      won: player.winner,
      setup: playerSetupFrom(module.scorePad, player.setup),
    })),
    tieWinner: "",
    groupOutcome: play.group ? (play.group.won ? "won" : "lost") : "",
    groupScore: play.group?.scores?.total?.toString() ?? "",
  }
}

/**
 * The form for playing a recorded Play again: the same Players in the same order, and, if the
 * Play was scored by the module that scores its Game today, the same choices. Only the date and
 * the scores are new.
 *
 * @param playerIds the Players that exist today; anyone removed since is left out
 */
export function playAgain(
  module: GameModule,
  game: Game,
  previous: Play,
  playerIds: string[],
): PlayFormValues {
  const blank = blankPlay(module, game)
  const sameModule = previous.gameModuleId === module.id
  return {
    ...blank,
    recordingMode: sameModule ? previous.recordingMode : blank.recordingMode,
    // The same Expansions and Variants. How it will be won is anyone's guess: the usual way.
    setup: sameModule ? setupFrom(module.scorePad, previous.setup) : blank.setup,
    players: previous.results
      .map((result) => result.player.id)
      .filter((playerId) => playerIds.includes(playerId))
      .map((playerId) => newPlayerRow(module.scorePad, playerId)),
  }
}
