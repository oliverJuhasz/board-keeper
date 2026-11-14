import { screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { expect, test } from "vitest"
import type { NewPlay, Play } from "@/api/client"
import {
  annaPlayer,
  bence,
  bencePlayer,
  doraPlayer,
  gamesAre,
  playCanBeEntered,
  playIs,
  playsCanBePreviewed,
  sevenWonders,
  signedInAs,
} from "@/test/api"
import { sevenWondersModule } from "@/test/gameModules"
import { renderApp } from "@/test/renderApp"
import { server } from "@/test/server"

// A Player Setup: what is chosen for each Player, such as their Wonder and its side in 7
// Wonders. Each Wonder board exists once, so two Players can't have the same one.

/** A Play of 7 Wonders as the server would answer it. */
function wondersPlay(): Play {
  return {
    id: "0193a000-0000-7000-8000-0000000000f9",
    game: { id: sevenWonders.id, name: sevenWonders.name, moduleKey: "seven-wonders" },
    gameModuleId: sevenWondersModule.id,
    playedOn: "2026-03-14",
    recordingMode: "POINTS",
    setup: {},
    cooperative: false,
    results: [
      {
        player: bencePlayer,
        scores: { wonder: 10, military: 9 },
        setup: { wonder: "babylon", side: "night" },
        markedWinner: false,
        total: 19,
        placing: 1,
        won: true,
      },
      {
        player: annaPlayer,
        scores: { wonder: 7, military: -2 },
        setup: { wonder: "giza" },
        markedWinner: false,
        total: 5,
        placing: 2,
        won: false,
      },
      {
        player: doraPlayer,
        scores: { wonder: 3, military: 0 },
        setup: {},
        markedWinner: false,
        total: 3,
        placing: 3,
        won: false,
      },
    ],
    recordedBy: bencePlayer,
    recordedAt: "2026-03-14T12:00:00Z",
    canChange: true,
  }
}

test("each player's wonder and side are chosen in their row, each wonder once", async () => {
  signedInAs(bence)
  gamesAre([sevenWonders])
  playCanBeEntered()
  playsCanBePreviewed()
  const sent: NewPlay[] = []
  server.use(
    http.post("*/api/plays", async ({ request }) => {
      sent.push((await request.json()) as NewPlay)
      return HttpResponse.json(wondersPlay())
    }),
  )
  playIs(wondersPlay())
  renderApp(`/plays/new?game=${sevenWonders.id}`)

  await userEvent.selectOptions(await screen.findByLabelText("Add a player"), "Anna")
  await userEvent.selectOptions(screen.getByLabelText("Add a player"), "Dora")
  await userEvent.selectOptions(screen.getByLabelText("Wonder board of Bence"), "Babylon")
  await userEvent.selectOptions(screen.getByLabelText("Side of Bence"), "Night")
  await userEvent.selectOptions(screen.getByLabelText("Wonder board of Anna"), "Giza")

  // Babylon is Bence's, so nobody else can have it.
  const doraWonder = screen.getByLabelText("Wonder board of Dora")
  expect(within(doraWonder).getByRole("option", { name: "Babylon" })).toBeDisabled()
  expect(within(doraWonder).getByRole("option", { name: "Rhodes" })).toBeEnabled()

  for (const [name, wonder, military] of [
    ["Bence", "10", "9"],
    ["Anna", "7", "-2"],
    ["Dora", "3", "0"],
  ]) {
    await userEvent.type(screen.getByLabelText(`Wonder of ${name}`), wonder)
    await userEvent.type(screen.getByLabelText(`Military of ${name}`), military)
  }
  await userEvent.click(screen.getByRole("button", { name: "Record play" }))

  await screen.findByRole("heading", { name: "7 Wonders" })
  // Only the choices that were made are sent.
  expect(sent[0].players.map((player) => player.setup)).toEqual([
    { wonder: "babylon", side: "night" },
    { wonder: "giza" },
    {},
  ])
})

test("a play's page shows each player's wonder and side", async () => {
  signedInAs(bence)
  playIs(wondersPlay())
  renderApp(`/plays/${wondersPlay().id}`)

  const results = await screen.findByRole("table", { name: "Results" })
  expect(within(results).getByText("Babylon · Night")).toBeInTheDocument()
  expect(within(results).getByText("Giza")).toBeInTheDocument()
})

test("editing a play keeps each player's choices", async () => {
  signedInAs(bence)
  gamesAre([sevenWonders])
  playCanBeEntered()
  playIs(wondersPlay())
  renderApp(`/plays/${wondersPlay().id}/edit`)

  await waitFor(() => expect(screen.getByLabelText("Wonder board of Bence")).toHaveValue("babylon"))
  expect(screen.getByLabelText("Side of Bence")).toHaveValue("night")
  expect(screen.getByLabelText("Wonder board of Anna")).toHaveValue("giza")
  expect(screen.getByLabelText("Wonder board of Dora")).toHaveValue("")
})
