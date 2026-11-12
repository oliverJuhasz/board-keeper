import { screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { expect, test } from "vitest"
import type { NewPlay, Play } from "@/api/client"
import { todayIso } from "@/lib/dates"
import {
  anna,
  annaPlayer,
  bence,
  bencePlayer,
  calico,
  calicoPlay,
  doraPlayer,
  gamesAre,
  pandemic,
  pandemicPlay,
  playCanBeEntered,
  playIs,
  problem,
  signedInAs,
} from "@/test/api"
import { renderApp } from "@/test/renderApp"
import { server } from "@/test/server"

/** Answers "record a play" with the given Play, and remembers what was sent. */
function playsCanBeRecorded(recorded: Play = calicoPlay) {
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
  gamesAre([calico])
  playCanBeEntered()
  renderApp("/")

  await userEvent.click(await screen.findByRole("link", { name: "Record a play of Calico" }))

  expect(await screen.findByRole("heading", { name: "Record a play" })).toBeInTheDocument()
  expect(screen.getByText("Who played Calico, and how it ended.")).toBeInTheDocument()
  expect(screen.getByText("Points, highest wins")).toBeInTheDocument()
})

test("someone who may not record plays is not offered to", async () => {
  signedInAs({ ...bence, permissions: ["CREATE_GAMES"] })
  gamesAre([calico])
  renderApp("/")

  await screen.findByRole("row", { name: /Calico/ })
  expect(screen.queryByRole("link", { name: "Record a play of Calico" })).not.toBeInTheDocument()
})

test("records a competitive play in points and then shows it", async () => {
  signedInAs(bence)
  gamesAre([calico])
  playCanBeEntered()
  const sent = playsCanBeRecorded()
  renderApp(`/plays/new?game=${calico.id}`)

  // Whoever records is put in as the first player.
  expect(await screen.findByLabelText("Score of Bence")).toBeInTheDocument()
  await addPlayer("Dora")
  await userEvent.type(screen.getByLabelText("Score of Bence"), "48")
  await userEvent.type(screen.getByLabelText("Score of Dora"), "61")
  await userEvent.type(screen.getByLabelText("Location"), " At Dora's ")
  await userEvent.type(screen.getByLabelText("Notes"), "Dora's first play.")
  await submit()

  // The page of the recorded play, as the server answered it.
  expect(await screen.findByRole("heading", { name: "Calico" })).toBeInTheDocument()
  expect(sent).toEqual([
    {
      gameId: calico.id,
      playedOn: todayIso(),
      location: "At Dora's",
      notes: "Dora's first play.",
      recordingMode: "POINTS",
      setup: { cooperative: "false", lowestScoreWins: "false" },
      players: [
        { playerId: bencePlayer.id, scores: { total: 48 }, winner: false },
        { playerId: doraPlayer.id, scores: { total: 61 }, winner: false },
      ],
    },
  ])
})

test("a player can be taken out again, and is then on offer again", async () => {
  signedInAs(bence)
  gamesAre([calico])
  playCanBeEntered()
  renderApp(`/plays/new?game=${calico.id}`)

  await addPlayer("Anna")
  const offered = () =>
    within(screen.getByLabelText("Add a player"))
      .getAllByRole("option")
      .map((option) => option.textContent)
  expect(offered()).toEqual(["Choose a player…", "Dora"])

  await userEvent.click(screen.getByRole("button", { name: "Remove Anna" }))

  expect(screen.queryByLabelText("Score of Anna")).not.toBeInTheDocument()
  expect(offered()).toEqual(["Choose a player…", "Anna", "Dora"])
})

test("asks who won a tie for first place, and shares the win unless told", async () => {
  signedInAs(bence)
  gamesAre([calico])
  playCanBeEntered()
  const sent = playsCanBeRecorded()
  renderApp(`/plays/new?game=${calico.id}`)

  await addPlayer("Anna")
  await addPlayer("Dora")
  await userEvent.type(await screen.findByLabelText("Score of Bence"), "70")
  await userEvent.type(screen.getByLabelText("Score of Anna"), "70")
  expect(screen.queryByText("Tie for first place")).not.toBeInTheDocument()
  await userEvent.type(screen.getByLabelText("Score of Dora"), "12")

  expect(screen.getByText("Tie for first place")).toBeInTheDocument()
  expect(screen.getByRole("radio", { name: "Shared win" })).toBeChecked()
  // Only the two who are tied can be given the win.
  expect(screen.queryByRole("radio", { name: "Dora" })).not.toBeInTheDocument()
  await userEvent.click(screen.getByRole("radio", { name: "Anna" }))
  await submit()

  await screen.findByRole("heading", { name: "Calico" })
  expect(sent[0].players.map((player) => player.winner)).toEqual([false, true, false])
})

test("checks the players and their scores before sending anything", async () => {
  signedInAs(bence)
  gamesAre([calico])
  playCanBeEntered()
  const sent = playsCanBeRecorded()
  renderApp(`/plays/new?game=${calico.id}`)

  await screen.findByLabelText("Score of Bence")
  await submit()
  expect(await screen.findByText("Add at least two players.")).toBeInTheDocument()

  await addPlayer("Anna")
  await userEvent.type(screen.getByLabelText("Score of Anna"), "74")
  await submit()
  expect(await screen.findByText("Enter a score.")).toBeInTheDocument()
  expect(screen.queryByText("Add at least two players.")).not.toBeInTheDocument()

  // The message goes away as soon as its cause is fixed.
  await userEvent.type(screen.getByLabelText("Score of Bence"), "-3")
  expect(screen.queryByText("Enter a score.")).not.toBeInTheDocument()
  expect(sent).toEqual([])
})

test("records only the winner for a game that is recorded that way", async () => {
  signedInAs(bence)
  gamesAre([{ ...calico, recordingMode: "WINNER_ONLY" }])
  playCanBeEntered()
  const sent = playsCanBeRecorded()
  renderApp(`/plays/new?game=${calico.id}`)

  await addPlayer("Anna")
  expect(screen.queryByLabelText("Score of Anna")).not.toBeInTheDocument()
  await submit()
  expect(await screen.findByText("Tick at least one winner.")).toBeInTheDocument()

  await userEvent.click(screen.getByRole("checkbox", { name: "Anna won" }))
  await submit()

  await screen.findByRole("heading", { name: "Calico" })
  expect(sent).toEqual([
    {
      gameId: calico.id,
      playedOn: todayIso(),
      recordingMode: "WINNER_ONLY",
      setup: { cooperative: "false", lowestScoreWins: "false" },
      players: [
        { playerId: bencePlayer.id, winner: false },
        { playerId: annaPlayer.id, winner: true },
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
      gameId: pandemic.id,
      playedOn: todayIso(),
      recordingMode: "WINNER_ONLY",
      setup: { cooperative: "true", lowestScoreWins: "false" },
      players: [
        { playerId: annaPlayer.id, winner: false },
        { playerId: bencePlayer.id, winner: false },
      ],
      group: { won: true },
    },
  ])
})

test("how a play is recorded can be changed for that one play", async () => {
  signedInAs(anna)
  gamesAre([calico])
  playCanBeEntered()
  const sent = playsCanBeRecorded(pandemicPlay)
  renderApp(`/plays/new?game=${calico.id}`)

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
    players: [{ playerId: annaPlayer.id, winner: false }],
    group: { won: false, scores: { total: 17 } },
  })
})

test("shows the game module's own reason when it refuses a play", async () => {
  signedInAs(bence)
  gamesAre([calico])
  playCanBeEntered({ "generic.invalid.scoreMissing": "Enter a score for every player." })
  server.use(
    http.post("*/api/plays", () =>
      HttpResponse.json(
        { status: 422, code: "plays.invalidEntry", reason: "generic.invalid.scoreMissing" },
        { status: 422 },
      ),
    ),
  )
  renderApp(`/plays/new?game=${calico.id}`)

  await addPlayer("Anna")
  await userEvent.type(await screen.findByLabelText("Score of Bence"), "1")
  await userEvent.type(screen.getByLabelText("Score of Anna"), "2")
  await submit()

  expect(await screen.findByText("Enter a score for every player.")).toBeInTheDocument()
})

test("says why the server refused a play", async () => {
  signedInAs(bence)
  gamesAre([calico])
  playCanBeEntered()
  server.use(http.post("*/api/plays", () => problem(422, "plays.invalidEntry")))
  renderApp(`/plays/new?game=${calico.id}`)

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
  gamesAre([calico])
  playCanBeEntered()
  renderApp("/plays/new")

  expect(await screen.findByRole("heading", { name: "Which game?" })).toBeInTheDocument()
  expect(screen.getByRole("link", { name: "Go to Games" })).toHaveAttribute("href", "/")
})

test("shows a competitive play with the best placing first", async () => {
  signedInAs(bence)
  playIs(calicoPlay)
  renderApp(`/plays/${calicoPlay.id}`)

  expect(await screen.findByRole("heading", { name: "Calico" })).toBeInTheDocument()
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
    `/plays/new?game=${calico.id}`,
  )
})

test("shows only who won when that is all that was recorded", async () => {
  signedInAs(bence)
  const play: Play = {
    ...calicoPlay,
    recordingMode: "WINNER_ONLY",
    // Entered with the winner last, and shown with the winner first.
    results: [
      { player: annaPlayer, scores: {}, markedWinner: false, won: false },
      { player: doraPlayer, scores: {}, markedWinner: true, won: true },
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
