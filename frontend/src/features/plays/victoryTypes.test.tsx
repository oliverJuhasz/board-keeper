import { waitFor } from "@testing-library/react"
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
import { playerRow, scoreField } from "@/test/playForm"
import { inside, page } from "@/test/queries"
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
      { player: bencePlayer, scores: {}, setup: {}, markedWinner: true, won: true },
      { player: doraPlayer, scores: {}, setup: {}, markedWinner: false, won: false },
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
  await userEvent.selectOptions(await page.findByTestId("players-to-add"), "Dora")
}

/** The rows of the score pad in the form, without its total and Placing. */
function padRows() {
  return page.queryAllByTestId("score-pad-category").map((row) => row.textContent)
}

test("a play is won on points unless the recorder says otherwise", async () => {
  playsCanBePreviewed()
  const sent = playsCanBeRecorded(duelPlay({ recordingMode: "POINTS", victoryType: "civilian" }))
  await startRecording()

  expect(page.getByTestId("victory-type-civilian")).toBeChecked()
  expect(padRows()).toEqual(["Civilian Buildings (blue)", "Wonders"])
  await userEvent.type(scoreField("Bence", "civilian"), "20")
  await userEvent.type(scoreField("Bence", "wonders"), "15")
  await userEvent.type(scoreField("Dora", "civilian"), "12")
  await userEvent.type(scoreField("Dora", "wonders"), "13")
  await userEvent.click(page.getByTestId("save-play"))

  await page.findByTestIdWith("page-title", "7 Wonders Duel")
  expect(sent[0]).toMatchObject({
    recordingMode: "POINTS",
    victoryType: "civilian",
    setup: { pantheon: "false", agora: "false" },
  })
})

test("a supremacy has no score pad: the recorder ticks its one winner", async () => {
  const sent = playsCanBeRecorded(duelPlay())
  await startRecording()

  await userEvent.click(page.getByTestId("victory-type-militarySupremacy"))

  expect(padRows()).toEqual([])
  expect(page.getByTestId("outright-hint")).toHaveTextContent(
    "The game ended there and nobody counted points: tick who won.",
  )
  // Only the winner is recorded, so there is no choosing how.
  expect(page.queryByTestId("change-recording")).not.toBeInTheDocument()
  await userEvent.click(playerRow("Bence").getByTestId("won"))
  await userEvent.click(playerRow("Dora").getByTestId("won"))
  await userEvent.click(page.getByTestId("save-play"))
  expect(
    await page.findByTestIdWith("field-error", "Only one player can win this way."),
  ).toBeInTheDocument()

  await userEvent.click(playerRow("Dora").getByTestId("won"))
  await userEvent.click(page.getByTestId("save-play"))
  await page.findByTestIdWith("page-title", "7 Wonders Duel")
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

  expect(page.queryByTestId("victory-type-politicalSupremacy")).not.toBeInTheDocument()
  await userEvent.click(page.getByTestId("expansion-agora"))

  expect(padRows()).toEqual(["Civilian Buildings (blue)", "Wonders", "Senate"])
  await userEvent.click(page.getByTestId("victory-type-politicalSupremacy"))
  // Without Agora again, political supremacy isn't possible, and the usual way takes its place.
  await userEvent.click(page.getByTestId("expansion-agora"))
  expect(page.getByTestId("victory-type-civilian")).toBeChecked()

  await userEvent.click(page.getByTestId("expansion-agora"))
  await userEvent.click(page.getByTestId("victory-type-politicalSupremacy"))
  await userEvent.click(playerRow("Dora").getByTestId("won"))
  await userEvent.click(page.getByTestId("save-play"))
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

  expect(await page.findByTestId("won-by")).toHaveTextContent("Won by: Military supremacy")
  expect(page.getByTestId("recorded-by")).toHaveTextContent(/^Winner only, Pantheon\./)
})

test("editing a play keeps how it was won", async () => {
  signedInAs(bence)
  gamesAre([duel])
  playCanBeEntered()
  playIs(duelPlay())
  renderApp(`/plays/${duelPlay().id}/edit`)

  expect(await page.findByTestId("victory-type-militarySupremacy")).toBeChecked()
  expect(page.getByTestId("expansion-pantheon")).toBeChecked()
  expect(playerRow("Bence").getByTestId("won")).toBeChecked()
})

test("the history names how a play was won", async () => {
  signedInAs(bence)
  historyIs([duelPlay()])
  renderApp("/plays")

  const play = await page.findByTestIdWith("play-summary", "7 Wonders Duel")
  expect(inside(play).getByTestId("won-by")).toHaveTextContent("· Military supremacy")
})
