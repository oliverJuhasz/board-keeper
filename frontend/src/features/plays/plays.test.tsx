import { waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { expect, test } from "vitest"
import type { NewPlay, Play } from "@/api/client"
import { todayIso } from "@/lib/dates"
import {
  aNewPlayId,
  anna,
  annaPlayer,
  azul,
  azulPlay,
  bence,
  bencePlayer,
  doraPlayer,
  gamesAre,
  pandemic,
  pandemicPlay,
  playCanBeEntered,
  playIs,
  playsCanBePreviewed,
  problem,
  signedInAs,
} from "@/test/api"
import { findScoreField, playerRow, queryScoreField, scoreField } from "@/test/playForm"
import { inside, page } from "@/test/queries"
import { renderApp } from "@/test/renderApp"
import { server } from "@/test/server"

/** Answers "record a play" with the given Play, and remembers what was sent. */
function playsCanBeRecorded(recorded: Play = azulPlay) {
  const sent: NewPlay[] = []
  server.use(
    http.post("*/api/plays", async ({ request }) => {
      sent.push((await request.json()) as NewPlay)
      return HttpResponse.json(recorded)
    }),
  )
  playIs(recorded)
  return sent
}

async function addPlayer(name: string) {
  await userEvent.selectOptions(await page.findByTestId("players-to-add"), name)
}

async function submit() {
  await userEvent.click(page.getByTestId("save-play"))
}

test("a game's row leads to recording a play of it", async () => {
  signedInAs(bence)
  gamesAre([azul])
  playCanBeEntered()
  renderApp("/")

  await userEvent.click(await page.findByTestIdWith("record-play", "Azul"))

  // The start page has the same heading, and stays until the form's code has loaded: wait for
  // what only the form says.
  expect(
    await page.findByTestIdWith("page-description", "Who played Azul, and how it ended."),
  ).toBeInTheDocument()
  expect(page.getByTestIdWith("page-title", "Record a play")).toBeInTheDocument()
  expect(page.getByTestId("recorded-as")).toHaveTextContent("Points, highest wins")
})

test("someone who may not record plays is not offered to", async () => {
  signedInAs({ ...bence, permissions: ["CREATE_GAMES"] })
  gamesAre([azul])
  renderApp("/")

  await page.findByTestIdWith("game-row", "Azul")
  expect(page.queryByTestIdWith("record-play", "Azul")).not.toBeInTheDocument()
})

test("records a competitive play in points and then shows it", async () => {
  signedInAs(bence)
  gamesAre([azul])
  playCanBeEntered()
  playsCanBePreviewed()
  const sent = playsCanBeRecorded()
  renderApp(`/plays/new?game=${azul.id}`)

  // Whoever records is put in as the first player.
  expect(await findScoreField("Bence")).toBeInTheDocument()
  await addPlayer("Dora")
  await userEvent.type(scoreField("Bence"), "48")
  await userEvent.type(scoreField("Dora"), "61")
  await userEvent.type(page.getByTestId("location"), " At Dora's ")
  await userEvent.type(page.getByTestId("notes"), "Dora's first play.")
  await submit()

  // The page of the recorded play, as the server answered it.
  expect(await page.findByTestIdWith("page-title", "Azul")).toBeInTheDocument()
  expect(sent).toEqual([
    {
      id: aNewPlayId,
      gameId: azul.id,
      playedOn: todayIso(),
      location: "At Dora's",
      notes: "Dora's first play.",
      recordingMode: "POINTS",
      setup: { cooperative: "false", lowestScoreWins: "false" },
      players: [
        { playerId: bencePlayer.id, scores: { total: 48 }, winner: false, setup: {} },
        { playerId: doraPlayer.id, scores: { total: 61 }, winner: false, setup: {} },
      ],
    },
  ])
})

test("a player can be taken out again, and is then on offer again", async () => {
  signedInAs(bence)
  gamesAre([azul])
  playCanBeEntered()
  renderApp(`/plays/new?game=${azul.id}`)

  await addPlayer("Anna")
  const offered = () =>
    Array.from((page.getByTestId("players-to-add") as HTMLSelectElement).options).map(
      (option) => option.textContent,
    )
  expect(offered()).toEqual(["Choose a player…", "Dora", "Add a new player…"])

  await userEvent.click(playerRow("Anna").getByTestId("remove-player"))

  expect(queryScoreField("Anna")).not.toBeInTheDocument()
  expect(offered()).toEqual(["Choose a player…", "Anna", "Dora", "Add a new player…"])
})

test("someone who has never played can be added without leaving the form", async () => {
  signedInAs(bence)
  gamesAre([azul])
  playCanBeEntered()
  playsCanBePreviewed()
  const sent = playsCanBeRecorded()
  const eszter = { id: "0193a000-0000-7000-8000-0000000000b9", name: "Eszter" }
  server.use(http.post("*/api/players", () => HttpResponse.json(eszter)))
  renderApp(`/plays/new?game=${azul.id}`)

  await addPlayer("Add a new player…")
  expect(await page.findByTestId("player-name-dialog")).toHaveAccessibleName("Add a player")
  await userEvent.type(page.getByTestId("player-name"), "Eszter")
  await userEvent.click(page.getByTestId("save-player"))

  // She is in the play at once, and adding her did not submit the play's own form.
  expect(await findScoreField("Eszter")).toBeInTheDocument()
  expect(page.queryByTestId("player-name-dialog")).not.toBeInTheDocument()
  expect(page.queryByTestIdWith("field-error", "Enter a score.")).not.toBeInTheDocument()
  expect(sent).toEqual([])

  await userEvent.type(scoreField("Bence"), "48")
  await userEvent.type(scoreField("Eszter"), "61")
  await submit()

  await page.findByTestIdWith("page-title", "Azul")
  expect(sent[0].players).toEqual([
    { playerId: bencePlayer.id, scores: { total: 48 }, winner: false, setup: {} },
    { playerId: eszter.id, scores: { total: 61 }, winner: false, setup: {} },
  ])
})

test("after adding a player, the list is ready for the next one", async () => {
  signedInAs(bence)
  gamesAre([azul])
  playCanBeEntered()
  const eszter = { id: "0193a000-0000-7000-8000-0000000000b9", name: "Eszter" }
  server.use(http.post("*/api/players", () => HttpResponse.json(eszter)))
  renderApp(`/plays/new?game=${azul.id}`)

  await addPlayer("Anna")
  expect(scoreField("Anna")).toBeInTheDocument()
  expect(page.getByTestId("players-to-add")).toHaveFocus()

  // Also once the dialog for someone new has closed, whether or not they were added.
  await addPlayer("Add a new player…")
  await page.findByTestId("player-name-dialog")
  await userEvent.keyboard("{Escape}")
  await waitFor(() => expect(page.getByTestId("players-to-add")).toHaveFocus())
  await addPlayer("Add a new player…")
  await page.findByTestId("player-name-dialog")
  await userEvent.type(page.getByTestId("player-name"), "Eszter{Enter}")
  expect(await findScoreField("Eszter")).toBeInTheDocument()
  await waitFor(() => expect(page.getByTestId("players-to-add")).toHaveFocus())
})

test("once nobody is left to add, focus moves on to the last one's score", async () => {
  signedInAs({ ...bence, permissions: ["CREATE_PLAYS"] })
  gamesAre([azul])
  playCanBeEntered()
  renderApp(`/plays/new?game=${azul.id}`)

  await addPlayer("Anna")
  expect(page.getByTestId("players-to-add")).toHaveFocus()
  await addPlayer("Dora")
  expect(scoreField("Dora")).toHaveFocus()
})

test("someone who may not create players is not offered to add one", async () => {
  signedInAs({ ...bence, permissions: ["CREATE_PLAYS"] })
  gamesAre([azul])
  playCanBeEntered()
  renderApp(`/plays/new?game=${azul.id}`)

  const list = await page.findByTestId("players-to-add")
  expect(inside(list).queryByTestId("new-player-option")).not.toBeInTheDocument()
  // With everyone in the play, there is nothing left to offer.
  await addPlayer("Anna")
  await addPlayer("Dora")
  expect(page.queryByTestId("players-to-add")).not.toBeInTheDocument()
  expect(
    page.getByTestIdWith("everyone-added", "Every player is in this play already."),
  ).toBeInTheDocument()
})

test("asks who won a tie for first place, and takes no answer for granted", async () => {
  signedInAs(bence)
  gamesAre([azul])
  playCanBeEntered()
  playsCanBePreviewed()
  const sent = playsCanBeRecorded()
  renderApp(`/plays/new?game=${azul.id}`)

  await addPlayer("Anna")
  await addPlayer("Dora")
  await userEvent.type(await findScoreField("Bence"), "70")
  await userEvent.type(scoreField("Anna"), "70")
  expect(page.queryByTestIdWith("tie-winner", "Tie for first place")).not.toBeInTheDocument()
  await userEvent.type(scoreField("Dora"), "12")

  // The server found the tie. The question comes with the game module's own explanation.
  expect(await page.findByTestIdWith("tie-winner", "Tie for first place")).toBeInTheDocument()
  expect(
    page.getByTestIdWith("tie-explanation", "Pick who won the tie, or leave the win shared."),
  ).toBeInTheDocument()
  // Only the two who are tied can be given the win.
  expect(page.queryByTestId("tie-winner-Dora")).not.toBeInTheDocument()
  expect(page.getByTestId("tie-winner-shared")).not.toBeChecked()

  await submit()
  expect(
    await page.findByTestIdWith("field-error", "Say who won the tie, or that the win is shared."),
  ).toBeInTheDocument()
  expect(sent).toEqual([])

  await userEvent.click(page.getByTestId("tie-winner-Anna"))
  expect(
    page.queryByTestIdWith("field-error", "Say who won the tie, or that the win is shared."),
  ).not.toBeInTheDocument()
  await submit()

  await page.findByTestIdWith("page-title", "Azul")
  expect(sent[0].players.map((player) => player.winner)).toEqual([false, true, false])
})

test("a shared win is an answer to a tie, and marks nobody", async () => {
  signedInAs(bence)
  gamesAre([azul])
  playCanBeEntered()
  playsCanBePreviewed()
  const sent = playsCanBeRecorded()
  renderApp(`/plays/new?game=${azul.id}`)

  await addPlayer("Anna")
  await userEvent.type(await findScoreField("Bence"), "70")
  await userEvent.type(scoreField("Anna"), "70")
  await userEvent.click(await page.findByTestId("tie-winner-shared"))
  await submit()

  await page.findByTestIdWith("page-title", "Azul")
  expect(sent[0].players.map((player) => player.winner)).toEqual([false, false])
})

test("asks about a tie even when the play is submitted before the form could", async () => {
  signedInAs(bence)
  gamesAre([azul])
  playCanBeEntered()
  playsCanBePreviewed()
  const sent = playsCanBeRecorded()
  renderApp(`/plays/new?game=${azul.id}`)

  await addPlayer("Anna")
  await userEvent.type(await findScoreField("Bence"), "70")
  // Typed and submitted in one go, before the form has asked the server about the scores.
  await userEvent.type(scoreField("Anna"), "70{Enter}")

  expect(
    await page.findByTestIdWith("field-error", "Say who won the tie, or that the win is shared."),
  ).toBeInTheDocument()
  expect(await page.findByTestIdWith("tie-winner", "Tie for first place")).toBeInTheDocument()
  expect(sent).toEqual([])
})

test("checks the players and their scores before sending anything", async () => {
  signedInAs(bence)
  gamesAre([azul])
  playCanBeEntered()
  playsCanBePreviewed()
  const sent = playsCanBeRecorded()
  renderApp(`/plays/new?game=${azul.id}`)

  await findScoreField("Bence")
  await submit()
  expect(
    await page.findByTestIdWith("field-error", "This game needs at least 2 players."),
  ).toBeInTheDocument()

  await addPlayer("Anna")
  await userEvent.type(scoreField("Anna"), "74")
  await submit()
  expect(await page.findByTestIdWith("field-error", "Enter a score.")).toBeInTheDocument()
  expect(
    page.queryByTestIdWith("field-error", "This game needs at least 2 players."),
  ).not.toBeInTheDocument()

  // The message goes away as soon as its cause is fixed.
  await userEvent.type(scoreField("Bence"), "-3")
  expect(page.queryByTestIdWith("field-error", "Enter a score.")).not.toBeInTheDocument()
  expect(sent).toEqual([])
})

test("records only the winner for a game that is recorded that way", async () => {
  signedInAs(bence)
  gamesAre([{ ...azul, recordingMode: "WINNER_ONLY" }])
  playCanBeEntered()
  const sent = playsCanBeRecorded()
  renderApp(`/plays/new?game=${azul.id}`)

  await addPlayer("Anna")
  expect(queryScoreField("Anna")).not.toBeInTheDocument()
  await submit()
  expect(
    await page.findByTestIdWith("field-error", "Tick at least one winner."),
  ).toBeInTheDocument()

  await userEvent.click(playerRow("Anna").getByTestId("won"))
  await submit()

  await page.findByTestIdWith("page-title", "Azul")
  expect(sent).toEqual([
    {
      id: aNewPlayId,
      gameId: azul.id,
      playedOn: todayIso(),
      recordingMode: "WINNER_ONLY",
      setup: { cooperative: "false", lowestScoreWins: "false" },
      players: [
        { playerId: bencePlayer.id, winner: false, setup: {} },
        { playerId: annaPlayer.id, winner: true, setup: {} },
      ],
    },
  ])
})

test("records a cooperative play as won or lost by the group", async () => {
  signedInAs(anna)
  gamesAre([pandemic])
  playCanBeEntered()
  const sent = playsCanBeRecorded(pandemicPlay)
  renderApp(`/plays/new?game=${pandemic.id}`)

  // Nothing is entered for the players one by one.
  await addPlayer("Bence")
  expect(queryScoreField("Bence")).not.toBeInTheDocument()
  expect(page.queryByTestId("won")).not.toBeInTheDocument()
  await submit()
  expect(
    await page.findByTestIdWith("field-error", "Say whether the group won or lost."),
  ).toBeInTheDocument()

  await userEvent.click(page.getByTestId("group-outcome-won"))
  await submit()

  await page.findByTestIdWith("page-title", "Pandemic")
  expect(sent).toEqual([
    {
      id: aNewPlayId,
      gameId: pandemic.id,
      playedOn: todayIso(),
      recordingMode: "WINNER_ONLY",
      setup: { cooperative: "true", lowestScoreWins: "false" },
      players: [
        { playerId: annaPlayer.id, winner: false, setup: {} },
        { playerId: bencePlayer.id, winner: false, setup: {} },
      ],
      group: { won: true },
    },
  ])
})

test("how a play is recorded can be changed for that one play", async () => {
  signedInAs(anna)
  gamesAre([azul])
  playCanBeEntered()
  const sent = playsCanBeRecorded(pandemicPlay)
  renderApp(`/plays/new?game=${azul.id}`)

  await userEvent.click(await page.findByTestId("change-recording"))
  await userEvent.click(page.getByTestId("recording-kind-cooperative"))

  expect(page.getByTestId("recorded-as")).toHaveTextContent("Cooperative, with a score")
  await userEvent.click(page.getByTestId("group-outcome-lost"))
  await userEvent.type(page.getByTestId("group-score"), "17")
  await submit()

  await page.findByTestIdWith("page-title", "Pandemic")
  expect(sent[0]).toMatchObject({
    recordingMode: "POINTS",
    setup: { cooperative: "true", lowestScoreWins: "false" },
    players: [{ playerId: annaPlayer.id, winner: false, setup: {} }],
    group: { won: false, scores: { total: 17 } },
  })
})

test("shows the game module's own reason when it refuses a play", async () => {
  signedInAs(bence)
  gamesAre([azul])
  playCanBeEntered()
  playsCanBePreviewed()
  server.use(
    http.post("*/api/plays", () =>
      HttpResponse.json(
        { status: 422, code: "plays.invalidEntry", reason: "generic.invalid.scoreMissing" },
        { status: 422 },
      ),
    ),
  )
  renderApp(`/plays/new?game=${azul.id}`)

  await addPlayer("Anna")
  await userEvent.type(await findScoreField("Bence"), "1")
  await userEvent.type(scoreField("Anna"), "2")
  await submit()

  expect(
    await page.findByTestIdWith("problem", "Enter a score for every player."),
  ).toBeInTheDocument()
})

test("says why the server refused a play", async () => {
  signedInAs(bence)
  gamesAre([azul])
  playCanBeEntered()
  playsCanBePreviewed()
  server.use(http.post("*/api/plays", () => problem(422, "plays.invalidEntry")))
  renderApp(`/plays/new?game=${azul.id}`)

  await addPlayer("Anna")
  await userEvent.type(await findScoreField("Bence"), "1")
  await userEvent.type(scoreField("Anna"), "2")
  await submit()

  expect(
    await page.findByTestIdWith(
      "problem",
      "These results don't fit together. Check them and try again.",
    ),
  ).toBeInTheDocument()
})

test("without a known game in the address, points to the Games page", async () => {
  signedInAs(bence)
  gamesAre([azul])
  playCanBeEntered()
  renderApp("/plays/new")

  expect(await page.findByTestIdWith("page-title", "Which game?")).toBeInTheDocument()
  expect(page.getByTestId("go-to-games")).toHaveAttribute("href", "/")
})

test("shows a competitive play with the best placing first", async () => {
  signedInAs(bence)
  playIs(azulPlay)
  renderApp(`/plays/${azulPlay.id}`)

  expect(await page.findByTestIdWith("page-title", "Azul")).toBeInTheDocument()
  expect(page.getByTestId("page-description")).toHaveTextContent("14 March 2026 · At Dora's")
  expect(page.getByTestId("results-header")).toHaveTextContent("PlacingPlayerScore")
  expect(page.getAllByTestId("result-row").map((row) => row.textContent)).toEqual([
    "1AnnaWinner74",
    "2Dora61",
  ])
  expect(page.getByTestId("notes")).toHaveTextContent("Dora's first play.")
  expect(page.getByTestId("recorded-by")).toHaveTextContent(
    /Points, highest wins\. Recorded by Bence on 14 March 2026/,
  )
  expect(page.getByTestId("record-another")).toHaveAttribute(
    "href",
    `/plays/new?game=${azul.id}&another=${azulPlay.id}`,
  )
})

test("another play after one starts with the same players, day and place", async () => {
  signedInAs(bence)
  gamesAre([azul])
  playCanBeEntered()
  playsCanBePreviewed()
  const sent = playsCanBeRecorded()
  renderApp(`/plays/${azulPlay.id}`)

  await userEvent.click(await page.findByTestId("record-another"))

  // The same players in the same order, with only their scores to enter. Bence recorded the
  // play without playing in it, so he isn't put in.
  expect(await findScoreField("Dora")).toHaveValue(null)
  expect(page.getAllByTestId("score").map((input) => input.getAttribute("aria-label"))).toEqual([
    "Score of Dora",
    "Score of Anna",
  ])
  expect(page.getByTestId("played-on")).toHaveValue("2026-03-14")
  expect(page.getByTestId("location")).toHaveValue("At Dora's")
  // Notes belong to the one play.
  expect(page.getByTestId("notes")).toHaveValue("")

  await userEvent.type(scoreField("Dora"), "70")
  await userEvent.type(scoreField("Anna"), "52")
  await submit()

  await page.findByTestIdWith("page-title", "Azul")
  expect(sent).toEqual([
    {
      id: aNewPlayId,
      gameId: azul.id,
      playedOn: "2026-03-14",
      location: "At Dora's",
      recordingMode: "POINTS",
      setup: { cooperative: "false", lowestScoreWins: "false" },
      players: [
        { playerId: doraPlayer.id, scores: { total: 70 }, winner: false, setup: {} },
        { playerId: annaPlayer.id, scores: { total: 52 }, winner: false, setup: {} },
      ],
    },
  ])
})

test("shows only who won when that is all that was recorded", async () => {
  signedInAs(bence)
  const play: Play = {
    ...azulPlay,
    recordingMode: "WINNER_ONLY",
    // Entered with the winner last, and shown with the winner first.
    results: [
      { player: annaPlayer, scores: {}, setup: {}, markedWinner: false, won: false },
      { player: doraPlayer, scores: {}, setup: {}, markedWinner: true, won: true },
    ],
  }
  playIs(play)
  renderApp(`/plays/${play.id}`)

  expect(await page.findByTestId("results-header")).toHaveTextContent(/^Player$/)
  expect(page.getAllByTestId("result-row").map((row) => row.textContent)).toEqual([
    "DoraWinner",
    "Anna",
  ])
})

test("shows a cooperative play as won or lost together", async () => {
  signedInAs(bence)
  playIs(pandemicPlay)
  renderApp(`/plays/${pandemicPlay.id}`)

  expect(await page.findByTestId("group-outcome")).toHaveTextContent("Lost together")
  expect(page.getByTestId("group-total")).toHaveTextContent("Score: 17")
  expect(page.getAllByTestId("result-row").map((row) => row.textContent)).toEqual(["Anna", "Bence"])
  expect(page.queryByTestId("results")).not.toBeInTheDocument()
})

test("says so when a play doesn't exist", async () => {
  signedInAs(bence)
  server.use(http.get("*/api/plays/:id", () => problem(404, "plays.notFound")))
  renderApp("/plays/0193a000-0000-7000-8000-00000000dead")

  expect(
    await page.findByTestIdWith("page-title", "This play no longer exists."),
  ).toBeInTheDocument()
})
