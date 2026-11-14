import { screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { expect, test } from "vitest"
import type { NewPlay, Play } from "@/api/client"
import {
  bence,
  bencePlayer,
  doraPlayer,
  duel,
  gamesAre,
  historyIs,
  playCanBeEntered,
  playIs,
  playsCanBePreviewed,
  signedInAs,
} from "@/test/api"
import { duelModule } from "@/test/gameModules"
import { renderApp } from "@/test/renderApp"
import { server } from "@/test/server"

// A Game won in more than one way, with Expansions: 7 Wonders Duel is won on points, or
// outright by a supremacy, in which case nobody counts points and only the winner is recorded.
// Agora adds a row to the pad and a way to win.

/** A Play of 7 Wonders Duel as the server would answer it: Bence won by military supremacy. */
function duelPlay(overrides: Partial<Play> = {}): Play {
  return {
    id: "0193a000-0000-7000-8000-0000000000f8",
    game: { id: duel.id, name: duel.name, moduleKey: "seven-wonders-duel" },
    gameModuleId: duelModule.id,
    playedOn: "2026-03-14",
    recordingMode: "WINNER_ONLY",
    setup: { pantheon: "true", agora: "false" },
    victoryType: "militarySupremacy",
    cooperative: false,
    results: [
      { player: bencePlayer, scores: {}, markedWinner: true, won: true },
      { player: doraPlayer, scores: {}, markedWinner: false, won: false },
    ],
    recordedBy: bencePlayer,
    recordedAt: "2026-03-14T12:00:00Z",
    canChange: true,
    ...overrides,
  }
}

function playsCanBeRecorded(answer: Play) {
  const sent: NewPlay[] = []
  server.use(
    http.post("*/api/plays", async ({ request }) => {
      sent.push((await request.json()) as NewPlay)
      return HttpResponse.json(answer)
    }),
  )
  playIs(answer)
  return sent
}

async function startRecording() {
  signedInAs(bence)
  gamesAre([duel])
  playCanBeEntered()
  renderApp(`/plays/new?game=${duel.id}`)
  await userEvent.selectOptions(await screen.findByLabelText("Add a player"), "Dora")
}

/** The rows of the score pad in the form, without its total and Placing. */
function padRows() {
  const pad = screen.queryByRole("table", { name: "Score pad" })
  return pad ? Array.from(pad.querySelectorAll("tbody th")).map((row) => row.textContent) : []
}

test("a play is won on points unless the recorder says otherwise", async () => {
  playsCanBePreviewed()
  const sent = playsCanBeRecorded(duelPlay({ recordingMode: "POINTS", victoryType: "civilian" }))
  await startRecording()

  expect(screen.getByRole("radio", { name: "Civilian victory (on points)" })).toBeChecked()
  expect(padRows()).toEqual(["Civilian Buildings (blue)", "Wonders"])
  await userEvent.type(screen.getByLabelText("Civilian Buildings (blue) of Bence"), "20")
  await userEvent.type(screen.getByLabelText("Wonders of Bence"), "15")
  await userEvent.type(screen.getByLabelText("Civilian Buildings (blue) of Dora"), "12")
  await userEvent.type(screen.getByLabelText("Wonders of Dora"), "13")
  await userEvent.click(screen.getByRole("button", { name: "Record play" }))

  await screen.findByRole("heading", { name: "7 Wonders Duel" })
  expect(sent[0]).toMatchObject({
    recordingMode: "POINTS",
    victoryType: "civilian",
    setup: { pantheon: "false", agora: "false" },
  })
})

test("a supremacy has no score pad: the recorder ticks its one winner", async () => {
  const sent = playsCanBeRecorded(duelPlay())
  await startRecording()

  await userEvent.click(screen.getByRole("radio", { name: "Military supremacy" }))

  expect(padRows()).toEqual([])
  expect(
    screen.getByText("The game ended there and nobody counted points: tick who won."),
  ).toBeInTheDocument()
  // Only the winner is recorded, so there is no choosing how.
  expect(screen.queryByRole("button", { name: "Change" })).not.toBeInTheDocument()
  await userEvent.click(screen.getByRole("checkbox", { name: "Bence won" }))
  await userEvent.click(screen.getByRole("checkbox", { name: "Dora won" }))
  await userEvent.click(screen.getByRole("button", { name: "Record play" }))
  expect(await screen.findByText("Only one player can win this way.")).toBeInTheDocument()

  await userEvent.click(screen.getByRole("checkbox", { name: "Dora won" }))
  await userEvent.click(screen.getByRole("button", { name: "Record play" }))
  await screen.findByRole("heading", { name: "7 Wonders Duel" })
  expect(sent[0]).toMatchObject({
    recordingMode: "WINNER_ONLY",
    victoryType: "militarySupremacy",
    players: [
      { playerId: bencePlayer.id, winner: true },
      { playerId: doraPlayer.id, winner: false },
    ],
  })
  expect(sent[0].players[0].scores).toBeUndefined()
})

test("an Expansion adds rows to the pad and ways to win", async () => {
  playsCanBePreviewed()
  const sent = playsCanBeRecorded(duelPlay({ victoryType: "politicalSupremacy" }))
  await startRecording()

  expect(screen.queryByRole("radio", { name: "Political supremacy" })).not.toBeInTheDocument()
  await userEvent.click(screen.getByRole("checkbox", { name: "Agora" }))

  expect(padRows()).toEqual(["Civilian Buildings (blue)", "Wonders", "Senate"])
  await userEvent.click(screen.getByRole("radio", { name: "Political supremacy" }))
  // Without Agora again, political supremacy isn't possible, and the usual way takes its place.
  await userEvent.click(screen.getByRole("checkbox", { name: "Agora" }))
  expect(screen.getByRole("radio", { name: "Civilian victory (on points)" })).toBeChecked()

  await userEvent.click(screen.getByRole("checkbox", { name: "Agora" }))
  await userEvent.click(screen.getByRole("radio", { name: "Political supremacy" }))
  await userEvent.click(screen.getByRole("checkbox", { name: "Dora won" }))
  await userEvent.click(screen.getByRole("button", { name: "Record play" }))
  await waitFor(() => expect(sent).toHaveLength(1))
  expect(sent[0]).toMatchObject({
    victoryType: "politicalSupremacy",
    setup: { pantheon: "false", agora: "true" },
  })
})

test("a play's page says how it was won and with which Expansions", async () => {
  signedInAs(bence)
  playIs(duelPlay())
  renderApp(`/plays/${duelPlay().id}`)

  expect(await screen.findByText("Won by: Military supremacy")).toBeInTheDocument()
  expect(screen.getByText(/^Winner only, Pantheon\./)).toBeInTheDocument()
})

test("editing a play keeps how it was won", async () => {
  signedInAs(bence)
  gamesAre([duel])
  playCanBeEntered()
  playIs(duelPlay())
  renderApp(`/plays/${duelPlay().id}/edit`)

  expect(await screen.findByRole("radio", { name: "Military supremacy" })).toBeChecked()
  expect(screen.getByRole("checkbox", { name: "Pantheon" })).toBeChecked()
  expect(screen.getByRole("checkbox", { name: "Bence won" })).toBeChecked()
})

test("the history names how a play was won", async () => {
  signedInAs(bence)
  historyIs([duelPlay()])
  renderApp("/plays")

  const play = await screen.findByRole("link", { name: /7 Wonders Duel/ })
  expect(within(play).getByText("· Military supremacy")).toBeInTheDocument()
})
