import { expect, test } from "vitest"
import type { Game, Play } from "@/api/client"
import { calicoModule, genericModule } from "@/test/gameModules"
import {
  blankPlay,
  categoriesOnPad,
  fromPlay,
  playFormFor,
  SHARED,
  toNewPlay,
  toPreviewRequest,
  type PlayFormValues,
} from "./playForm"

const anna = "0193a000-0000-7000-8000-0000000000b1"
const bence = "0193a000-0000-7000-8000-0000000000b2"
const dora = "0193a000-0000-7000-8000-0000000000b3"

/** A Player's row in the form for a Game without a module of its own: one score. */
function scored(playerId: string, total: string) {
  return { playerId, scores: { total }, won: false }
}

/** A Player's rows on Calico's score pad. */
function pad(playerId: string, designGoals: string, cats: string, buttons: string) {
  return { playerId, scores: { designGoals, cats, buttons }, won: false }
}

function form(overrides: Partial<PlayFormValues>): PlayFormValues {
  return {
    playedOn: "2026-03-14",
    location: "",
    notes: "",
    recordingMode: "POINTS",
    setup: {},
    victoryType: "",
    players: [],
    tieWinner: "",
    groupOutcome: "",
    groupScore: "",
    ...overrides,
  }
}

/** The fields the form would show a message at, with the name of the message. */
function problemsOf(module: typeof genericModule, values: PlayFormValues) {
  const checked = playFormFor(module).safeParse(values)
  return checked.success
    ? {}
    : Object.fromEntries(checked.error.issues.map((issue) => [issue.path.join("."), issue.message]))
}

const azul: Game = {
  id: "game",
  name: "Azul",
  gameModuleId: genericModule.id,
  moduleKey: "generic",
  hasOwnModule: false,
  cooperative: true,
  recordingMode: "WINNER_ONLY",
  lowestScoreWins: false,
}

test("a new play of a hand-added game starts with the game's own recording choices", () => {
  expect(blankPlay(genericModule, azul, anna)).toMatchObject({
    recordingMode: "WINNER_ONLY",
    setup: { cooperative: "true", lowestScoreWins: "false" },
    players: [{ playerId: anna, scores: { total: "" }, won: false }],
    tieWinner: "",
  })
})

test("a new play of a game with its own module starts with the module's defaults", () => {
  expect(blankPlay(calicoModule, { ...azul, gameModuleId: calicoModule.id }, anna)).toMatchObject({
    recordingMode: "POINTS",
    setup: { family: "false" },
    players: [{ playerId: anna, scores: { designGoals: "", cats: "", buttons: "" } }],
  })
})

test("a variant can take a row off the score pad", () => {
  const keys = (setup: Record<string, string>) =>
    categoriesOnPad(calicoModule.scorePad, setup).map((category) => category.key)

  expect(keys({})).toEqual(["designGoals", "cats", "buttons"])
  expect(keys({ family: "true" })).toEqual(["cats", "buttons"])
})

test("every row of the pad is sent as a number, with every variant", () => {
  const play = toNewPlay(
    calicoModule,
    "game",
    form({ players: [pad(anna, "28", "23", "21"), pad(dora, "21", "19", "18")] }),
  )

  expect(play).toEqual({
    gameId: "game",
    playedOn: "2026-03-14",
    location: undefined,
    notes: undefined,
    recordingMode: "POINTS",
    setup: { family: "false" },
    players: [
      { playerId: anna, scores: { designGoals: 28, cats: 23, buttons: 21 }, winner: false },
      { playerId: dora, scores: { designGoals: 21, cats: 19, buttons: 18 }, winner: false },
    ],
    group: undefined,
  })
})

test("a row that a variant took off the pad is not sent, whatever was typed in it", () => {
  const play = toNewPlay(
    calicoModule,
    "game",
    form({
      setup: { family: "true" },
      players: [pad(anna, "28", "23", "21"), pad(dora, "", "19", "18")],
    }),
  )

  expect(play.players.map((player) => player.scores)).toEqual([
    { cats: 23, buttons: 21 },
    { cats: 19, buttons: 18 },
  ])
})

test("the recorder's answer to a tie marks that player as the winner", () => {
  const values = form({ players: [scored(anna, "50"), scored(bence, "50")], tieWinner: bence })

  // The server said these two are tied.
  expect(
    toNewPlay(genericModule, "game", values, [anna, bence]).players.map((p) => p.winner),
  ).toEqual([false, true])
  // "Shared" marks nobody.
  expect(
    toNewPlay(genericModule, "game", { ...values, tieWinner: SHARED }, [anna, bence]).players.map(
      (p) => p.winner,
    ),
  ).toEqual([false, false])
})

test("an answer about a tie that no longer exists is not sent", () => {
  // Bence was given the tie at 50 all, and then Anna's score was corrected.
  const values = form({ players: [scored(anna, "55"), scored(bence, "50")], tieWinner: bence })

  expect(toNewPlay(genericModule, "game", values, []).players.map((p) => p.winner)).toEqual([
    false,
    false,
  ])
})

test("asking how a play would end never marks a winner", () => {
  const values = form({ players: [scored(anna, "50"), scored(bence, "50")], tieWinner: bence })

  expect(toPreviewRequest(genericModule, values)).toMatchObject({
    gameModuleId: genericModule.id,
    players: [{ winner: false }, { winner: false }],
  })
})

test("what doesn't count for the way a play is recorded is not sent", () => {
  // Scores and "lowest wins" were entered before the play was switched to cooperative.
  const play = toNewPlay(
    genericModule,
    "game",
    form({
      recordingMode: "WINNER_ONLY",
      setup: { cooperative: "true", lowestScoreWins: "true" },
      players: [{ playerId: anna, scores: { total: "50" }, won: true }],
      groupOutcome: "won",
      groupScore: "17",
    }),
  )

  expect(play.setup).toEqual({ cooperative: "true", lowestScoreWins: "false" })
  expect(play.players).toEqual([{ playerId: anna, scores: undefined, winner: false }])
  expect(play.group).toEqual({ won: true, scores: undefined })
})

test("a competitive play of a hand-added game needs two players, each with a whole number", () => {
  expect(problemsOf(genericModule, form({ players: [scored(anna, "50")] }))).toEqual({
    players: "validation.playersTooFew",
  })
  expect(
    problemsOf(
      genericModule,
      form({ players: [scored(anna, ""), scored(bence, "7.5"), scored(dora, "-3")] }),
    ),
  ).toEqual({
    "players.0.scores.total": "validation.scoreRequired",
    "players.1.scores.total": "validation.wholeNumber",
  })
})

test("a cooperative play needs one player and how it ended", () => {
  const cooperative = { cooperative: "true" }

  expect(problemsOf(genericModule, form({ setup: cooperative }))).toEqual({
    players: "validation.playerRequired",
    groupOutcome: "validation.outcomeRequired",
  })
  expect(
    problemsOf(
      genericModule,
      form({ setup: cooperative, players: [scored(anna, "")], groupOutcome: "lost" }),
    ),
  ).toEqual({})
})

test("each row of a score pad keeps to its own limits", () => {
  // Calico: nothing below zero, and buttons in threes.
  expect(
    problemsOf(
      calicoModule,
      form({ players: [pad(anna, "-1", "23", "20"), pad(dora, "21", "", "18")] }),
    ),
  ).toEqual({
    "players.0.scores.designGoals": "validation.scoreTooLow",
    "players.0.scores.buttons": "validation.scoreStep",
    "players.1.scores.cats": "validation.scoreRequired",
  })
})

test("a game has its own number of players", () => {
  const row = (playerId: string) => pad(playerId, "1", "1", "3")

  expect(problemsOf(calicoModule, form({ players: [] }))).toEqual({
    players: "validation.playerRequired",
  })
  // Calico can be played alone.
  expect(problemsOf(calicoModule, form({ players: [row(anna)] }))).toEqual({})
  expect(
    problemsOf(
      calicoModule,
      form({ players: [row(anna), row(bence), row(dora), row("d"), row("e")] }),
    ),
  ).toEqual({ players: "validation.playersTooMany" })
})

test("a play cannot be dated in the future", () => {
  const players = [scored(anna, "1"), scored(bence, "2")]

  expect(problemsOf(genericModule, form({ players, playedOn: "2999-01-01" }))).toEqual({
    playedOn: "validation.dateInFuture",
  })
})

/** A recorded Play as the API gives it, with only what matters here filled in. */
function recorded(overrides: Partial<Play>): Play {
  return {
    id: "play",
    game: { id: "game", name: "Azul" },
    gameModuleId: genericModule.id,
    playedOn: "2026-03-14",
    recordingMode: "POINTS",
    setup: { cooperative: "false", lowestScoreWins: "false" },
    cooperative: false,
    results: [],
    recordedBy: { id: anna, name: "Anna" },
    recordedAt: "2026-03-14T12:00:00Z",
    canChange: true,
    ...overrides,
  }
}

function result(playerId: string, total: number, markedWinner = false) {
  return {
    player: { id: playerId, name: playerId },
    scores: { total },
    markedWinner,
    total,
    placing: 1,
    won: true,
  }
}

test("a recorded play fills the form with what was entered for it", () => {
  const values = fromPlay(
    genericModule,
    recorded({
      location: "At Dora's",
      setup: { cooperative: "false", lowestScoreWins: "true" },
      results: [result(anna, 50), result(bence, 50, true)],
    }),
  )

  expect(values).toMatchObject({
    playedOn: "2026-03-14",
    location: "At Dora's",
    notes: "",
    setup: { cooperative: "false", lowestScoreWins: "true" },
    players: [scored(anna, "50"), { playerId: bence, scores: { total: "50" }, won: true }],
    tieWinner: bence,
  })
})

test("a tie that was left shared comes back as answered, not as a new question", () => {
  const values = fromPlay(
    genericModule,
    recorded({ results: [result(anna, 50), result(bence, 50)] }),
  )

  expect(values.tieWinner).toBe(SHARED)
})

test("saving an unchanged play sends what was recorded", () => {
  const competitive = recorded({ results: [result(anna, 50), result(bence, 50, true)] })
  expect(
    toNewPlay(genericModule, "game", fromPlay(genericModule, competitive), [anna, bence]).players,
  ).toEqual([
    { playerId: anna, scores: { total: 50 }, winner: false },
    { playerId: bence, scores: { total: 50 }, winner: true },
  ])

  const cooperative = recorded({
    cooperative: true,
    setup: { cooperative: "true", lowestScoreWins: "false" },
    group: { won: false, scores: { total: 17 }, total: 17 },
    results: [{ player: { id: anna, name: "Anna" }, scores: {}, markedWinner: false, won: false }],
  })
  expect(toNewPlay(genericModule, "game", fromPlay(genericModule, cooperative))).toMatchObject({
    setup: { cooperative: "true", lowestScoreWins: "false" },
    players: [{ playerId: anna, winner: false }],
    group: { won: false, scores: { total: 17 } },
  })
})
