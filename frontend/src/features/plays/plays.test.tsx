import { screen, waitFor, within } from "@testing-library/react"
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
  await userEvent.selectOptions(await screen.findByLabelText("Add a player"), name)
}

async function submit() {
  await userEvent.click(screen.getByRole("button", { name: "Record play" }))
}

test("a game's row leads to recording a play of it", async () => {
  signedInAs(bence)
  gamesAre([azul])
  playCanBeEntered()
  renderApp("/")

  await userEvent.click(await screen.findByRole("link", { name: "Record a play of Azul" }))

  // The start page has the same heading, and stays until the form's code has loaded: wait for
  // what only the form says.
  expect(await screen.findByText("Who played Azul, and how it ended.")).toBeInTheDocument()
  expect(screen.getByRole("heading", { name: "Record a play" })).toBeInTheDocument()
  expect(screen.getByText("Points, highest wins")).toBeInTheDocument()
})

test("someone who may not record plays is not offered to", async () => {
  signedInAs({ ...bence, permissions: ["CREATE_GAMES"] })
  gamesAre([azul])
  renderApp("/")

  await screen.findByRole("row", { name: /Azul/ })
  expect(screen.queryByRole("link", { name: "Record a play of Azul" })).not.toBeInTheDocument()
})

test("records a competitive play in points and then shows it", async () => {
  signedInAs(bence)
  gamesAre([azul])
  playCanBeEntered()
  playsCanBePreviewed()
  const sent = playsCanBeRecorded()
  renderApp(`/plays/new?game=${azul.id}`)

  // Whoever records is put in as the first player.
  expect(await screen.findByLabelText("Score of Bence")).toBeInTheDocument()
  await addPlayer("Dora")
  await userEvent.type(screen.getByLabelText("Score of Bence"), "48")
  await userEvent.type(screen.getByLabelText("Score of Dora"), "61")
  await userEvent.type(screen.getByLabelText("Location"), " At Dora's ")
  await userEvent.type(screen.getByLabelText("Notes"), "Dora's first play.")
  await submit()

  // The page of the recorded play, as the server answered it.
  expect(await screen.findByRole("heading", { name: "Azul" })).toBeInTheDocument()
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
    within(screen.getByLabelText("Add a player"))
      .getAllByRole("option")
      .map((option) => option.textContent)
  expect(offered()).toEqual(["Choose a player…", "Dora", "Add a new player…"])

  await userEvent.click(screen.getByRole("button", { name: "Remove Anna" }))

  expect(screen.queryByLabelText("Score of Anna")).not.toBeInTheDocument()
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
  const dialog = await screen.findByRole("dialog", { name: "Add a player" })
  await userEvent.type(within(dialog).getByLabelText("Name"), "Eszter")
  await userEvent.click(within(dialog).getByRole("button", { name: "Add player" }))

  // She is in the play at once, and adding her did not submit the play's own form.
  expect(await screen.findByLabelText("Score of Eszter")).toBeInTheDocument()
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument()
  expect(screen.queryByText("Enter a score.")).not.toBeInTheDocument()
  expect(sent).toEqual([])

  await userEvent.type(screen.getByLabelText("Score of Bence"), "48")
  await userEvent.type(screen.getByLabelText("Score of Eszter"), "61")
  await submit()

  await screen.findByRole("heading", { name: "Azul" })
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
  expect(screen.getByLabelText("Score of Anna")).toBeInTheDocument()
  expect(screen.getByLabelText("Add a player")).toHaveFocus()

  // Also once the dialog for someone new has closed, whether or not they were added.
  await addPlayer("Add a new player…")
  await screen.findByRole("dialog", { name: "Add a player" })
  await userEvent.keyboard("{Escape}")
  await waitFor(() => expect(screen.getByLabelText("Add a player")).toHaveFocus())
  await addPlayer("Add a new player…")
  const dialog = await screen.findByRole("dialog", { name: "Add a player" })
  await userEvent.type(within(dialog).getByLabelText("Name"), "Eszter{Enter}")
  expect(await screen.findByLabelText("Score of Eszter")).toBeInTheDocument()
  await waitFor(() => expect(screen.getByLabelText("Add a player")).toHaveFocus())
})

test("once nobody is left to add, focus moves on to the last one's score", async () => {
  signedInAs({ ...bence, permissions: ["CREATE_PLAYS"] })
  gamesAre([azul])
  playCanBeEntered()
  renderApp(`/plays/new?game=${azul.id}`)

  await addPlayer("Anna")
  expect(screen.getByLabelText("Add a player")).toHaveFocus()
  await addPlayer("Dora")
  expect(screen.getByLabelText("Score of Dora")).toHaveFocus()
})

test("someone who may not create players is not offered to add one", async () => {
  signedInAs({ ...bence, permissions: ["CREATE_PLAYS"] })
  gamesAre([azul])
  playCanBeEntered()
  renderApp(`/plays/new?game=${azul.id}`)

  const list = await screen.findByLabelText("Add a player")
  expect(within(list).queryByRole("option", { name: "Add a new player…" })).not.toBeInTheDocument()
  // With everyone in the play, there is nothing left to offer.
  await addPlayer("Anna")
  await addPlayer("Dora")
  expect(screen.queryByLabelText("Add a player")).not.toBeInTheDocument()
  expect(screen.getByText("Every player is in this play already.")).toBeInTheDocument()
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
  await userEvent.type(await screen.findByLabelText("Score of Bence"), "70")
  await userEvent.type(screen.getByLabelText("Score of Anna"), "70")
  expect(screen.queryByText("Tie for first place")).not.toBeInTheDocument()
  await userEvent.type(screen.getByLabelText("Score of Dora"), "12")

  // The server found the tie. The question comes with the game module's own explanation.
  expect(await screen.findByText("Tie for first place")).toBeInTheDocument()
  expect(screen.getByText("Pick who won the tie, or leave the win shared.")).toBeInTheDocument()
  // Only the two who are tied can be given the win.
  expect(screen.queryByRole("radio", { name: "Dora" })).not.toBeInTheDocument()
  expect(screen.getByRole("radio", { name: "Shared win" })).not.toBeChecked()

  await submit()
  expect(
    await screen.findByText("Say who won the tie, or that the win is shared."),
  ).toBeInTheDocument()
  expect(sent).toEqual([])

  await userEvent.click(screen.getByRole("radio", { name: "Anna" }))
  expect(
    screen.queryByText("Say who won the tie, or that the win is shared."),
  ).not.toBeInTheDocument()
  await submit()

  await screen.findByRole("heading", { name: "Azul" })
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
  await userEvent.type(await screen.findByLabelText("Score of Bence"), "70")
  await userEvent.type(screen.getByLabelText("Score of Anna"), "70")
  await userEvent.click(await screen.findByRole("radio", { name: "Shared win" }))
  await submit()

  await screen.findByRole("heading", { name: "Azul" })
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
  await userEvent.type(await screen.findByLabelText("Score of Bence"), "70")
  // Typed and submitted in one go, before the form has asked the server about the scores.
  await userEvent.type(screen.getByLabelText("Score of Anna"), "70{Enter}")

  expect(
    await screen.findByText("Say who won the tie, or that the win is shared."),
  ).toBeInTheDocument()
  expect(await screen.findByText("Tie for first place")).toBeInTheDocument()
  expect(sent).toEqual([])
})

test("checks the players and their scores before sending anything", async () => {
  signedInAs(bence)
  gamesAre([azul])
  playCanBeEntered()
  playsCanBePreviewed()
  const sent = playsCanBeRecorded()
  renderApp(`/plays/new?game=${azul.id}`)

  await screen.findByLabelText("Score of Bence")
  await submit()
  expect(await screen.findByText("This game needs at least 2 players.")).toBeInTheDocument()

  await addPlayer("Anna")
  await userEvent.type(screen.getByLabelText("Score of Anna"), "74")
  await submit()
  expect(await screen.findByText("Enter a score.")).toBeInTheDocument()
  expect(screen.queryByText("This game needs at least 2 players.")).not.toBeInTheDocument()

  // The message goes away as soon as its cause is fixed.
  await userEvent.type(screen.getByLabelText("Score of Bence"), "-3")
  expect(screen.queryByText("Enter a score.")).not.toBeInTheDocument()
  expect(sent).toEqual([])
})

test("records only the winner for a game that is recorded that way", async () => {
  signedInAs(bence)
  gamesAre([{ ...azul, recordingMode: "WINNER_ONLY" }])
  playCanBeEntered()
  const sent = playsCanBeRecorded()
  renderApp(`/plays/new?game=${azul.id}`)

  await addPlayer("Anna")
  expect(screen.queryByLabelText("Score of Anna")).not.toBeInTheDocument()
  await submit()
  expect(await screen.findByText("Tick at least one winner.")).toBeInTheDocument()

  await userEvent.click(screen.getByRole("checkbox", { name: "Anna won" }))
  await submit()

  await screen.findByRole("heading", { name: "Azul" })
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
  expect(screen.queryByLabelText("Score of Bence")).not.toBeInTheDocument()
  expect(screen.queryByRole("checkbox", { name: "Bence won" })).not.toBeInTheDocument()
  await submit()
  expect(await screen.findByText("Say whether the group won or lost.")).toBeInTheDocument()

  await userEvent.click(screen.getByRole("radio", { name: "The group won" }))
  await submit()

  await screen.findByRole("heading", { name: "Pandemic" })
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

  await userEvent.click(await screen.findByRole("button", { name: "Change" }))
  await userEvent.click(screen.getByRole("radio", { name: "Cooperative" }))

  expect(screen.getByText("Cooperative, with a score")).toBeInTheDocument()
  await userEvent.click(screen.getByRole("radio", { name: "The group lost" }))
  await userEvent.type(screen.getByLabelText("Score"), "17")
  await submit()

  await screen.findByRole("heading", { name: "Pandemic" })
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
  await userEvent.type(await screen.findByLabelText("Score of Bence"), "1")
  await userEvent.type(screen.getByLabelText("Score of Anna"), "2")
  await submit()

  expect(await screen.findByText("Enter a score for every player.")).toBeInTheDocument()
})

test("says why the server refused a play", async () => {
  signedInAs(bence)
  gamesAre([azul])
  playCanBeEntered()
  playsCanBePreviewed()
  server.use(http.post("*/api/plays", () => problem(422, "plays.invalidEntry")))
  renderApp(`/plays/new?game=${azul.id}`)

  await addPlayer("Anna")
  await userEvent.type(await screen.findByLabelText("Score of Bence"), "1")
  await userEvent.type(screen.getByLabelText("Score of Anna"), "2")
  await submit()

  expect(
    await screen.findByText("These results don't fit together. Check them and try again."),
  ).toBeInTheDocument()
})

test("without a known game in the address, points to the Games page", async () => {
  signedInAs(bence)
  gamesAre([azul])
  playCanBeEntered()
  renderApp("/plays/new")

  expect(await screen.findByRole("heading", { name: "Which game?" })).toBeInTheDocument()
  expect(screen.getByRole("link", { name: "Go to Games" })).toHaveAttribute("href", "/")
})

test("shows a competitive play with the best placing first", async () => {
  signedInAs(bence)
  playIs(azulPlay)
  renderApp(`/plays/${azulPlay.id}`)

  expect(await screen.findByRole("heading", { name: "Azul" })).toBeInTheDocument()
  expect(screen.getByText("14 March 2026 · At Dora's")).toBeInTheDocument()
  const rows = within(screen.getByRole("table", { name: "Results" })).getAllByRole("row")
  expect(rows.map((row) => row.textContent)).toEqual([
    "PlacingPlayerScore",
    "1AnnaWinner74",
    "2Dora61",
  ])
  expect(screen.getByText("Dora's first play.")).toBeInTheDocument()
  expect(
    screen.getByText(/Points, highest wins\. Recorded by Bence on 14 March 2026/),
  ).toBeInTheDocument()
  expect(screen.getByRole("link", { name: "Record another play" })).toHaveAttribute(
    "href",
    `/plays/new?game=${azul.id}`,
  )
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

  const rows = within(await screen.findByRole("table", { name: "Results" })).getAllByRole("row")
  expect(rows.map((row) => row.textContent)).toEqual(["Player", "DoraWinner", "Anna"])
})

test("shows a cooperative play as won or lost together", async () => {
  signedInAs(bence)
  playIs(pandemicPlay)
  renderApp(`/plays/${pandemicPlay.id}`)

  expect(await screen.findByText("Lost together")).toBeInTheDocument()
  expect(screen.getByText("Score: 17")).toBeInTheDocument()
  expect(screen.getByText("Anna")).toBeInTheDocument()
  expect(screen.getByText("Bence")).toBeInTheDocument()
  expect(screen.queryByRole("table")).not.toBeInTheDocument()
})

test("says so when a play doesn't exist", async () => {
  signedInAs(bence)
  server.use(http.get("*/api/plays/:id", () => problem(404, "plays.notFound")))
  renderApp("/plays/0193a000-0000-7000-8000-00000000dead")

  expect(
    await screen.findByRole("heading", { name: "This play no longer exists." }),
  ).toBeInTheDocument()
})
