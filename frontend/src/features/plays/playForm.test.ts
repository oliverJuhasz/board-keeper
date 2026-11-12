import { expect, test } from "vitest"
import { playForm, tiedForFirst, toNewPlay, type PlayForm } from "./playForm"

const anna = "0193a000-0000-7000-8000-0000000000b1"
const bence = "0193a000-0000-7000-8000-0000000000b2"
const dora = "0193a000-0000-7000-8000-0000000000b3"

function scored(playerId: string, score: string) {
  return { playerId, score, won: false }
}

function form(overrides: Partial<PlayForm>): PlayForm {
  return {
    playedOn: "2026-03-14",
    location: "",
    notes: "",
    cooperative: false,
    recordingMode: "POINTS",
    lowestScoreWins: false,
    players: [],
    tieWinner: "",
    groupOutcome: "",
    groupScore: "",
    ...overrides,
  }
}

/** The fields the form would show a message at, with the name of the message. */
function problemsOf(values: PlayForm) {
  const checked = playForm.safeParse(values)
  return checked.success
    ? {}
    : Object.fromEntries(checked.error.issues.map((issue) => [issue.path.join("."), issue.message]))
}

test("the players with the best score are tied for first place", () => {
  const players = [scored(anna, "50"), scored(bence, "50"), scored(dora, "20")]

  expect(tiedForFirst({ players, lowestScoreWins: false })).toEqual([anna, bence])
  // With the lowest score winning, Dora is first alone.
  expect(tiedForFirst({ players, lowestScoreWins: true })).toEqual([])
})

test("nobody is tied until every player has a score", () => {
  const players = [scored(anna, "50"), scored(bence, "50"), scored(dora, "")]

  expect(tiedForFirst({ players, lowestScoreWins: false })).toEqual([])
})

test("a tie the recorder decided marks that player as the winner", () => {
  const play = toNewPlay(
    "game",
    form({ players: [scored(anna, "50"), scored(bence, "50")], tieWinner: bence }),
  )

  expect(play.players).toEqual([
    { playerId: anna, scores: { total: 50 }, winner: false },
    { playerId: bence, scores: { total: 50 }, winner: true },
  ])
})

test("a decision about a tie that no longer exists is not sent", () => {
  // Bence was given the tie at 50 all, and then Anna's score was corrected.
  const play = toNewPlay(
    "game",
    form({ players: [scored(anna, "55"), scored(bence, "50")], tieWinner: bence }),
  )

  expect(play.players.map((player) => player.winner)).toEqual([false, false])
})

test("what doesn't count for the way a play is recorded is not sent", () => {
  // Scores and "lowest wins" were entered before the play was switched to cooperative.
  const play = toNewPlay(
    "game",
    form({
      cooperative: true,
      recordingMode: "WINNER_ONLY",
      lowestScoreWins: true,
      players: [{ playerId: anna, score: "50", won: true }],
      groupOutcome: "won",
      groupScore: "17",
    }),
  )

  expect(play.setup).toEqual({ cooperative: "true", lowestScoreWins: "false" })
  expect(play.players).toEqual([{ playerId: anna, scores: undefined, winner: false }])
  expect(play.group).toEqual({ won: true, scores: undefined })
})

test("a competitive play needs two players, each with a whole number as score", () => {
  expect(problemsOf(form({ players: [scored(anna, "50")] }))).toEqual({
    players: "validation.playersTooFew",
  })
  expect(
    problemsOf(form({ players: [scored(anna, ""), scored(bence, "7.5"), scored(dora, "-3")] })),
  ).toEqual({
    "players.0.score": "validation.scoreRequired",
    "players.1.score": "validation.wholeNumber",
  })
})

test("a cooperative play needs one player and how it ended", () => {
  expect(problemsOf(form({ cooperative: true }))).toEqual({
    players: "validation.playerRequired",
    groupOutcome: "validation.outcomeRequired",
  })
  expect(
    problemsOf(form({ cooperative: true, players: [scored(anna, "")], groupOutcome: "lost" })),
  ).toEqual({})
})

test("a play cannot be dated in the future", () => {
  const players = [scored(anna, "1"), scored(bence, "2")]

  expect(problemsOf(form({ players, playedOn: "2999-01-01" }))).toEqual({
    playedOn: "validation.dateInFuture",
  })
})
