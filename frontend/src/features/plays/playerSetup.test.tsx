import { waitFor } from "@testing-library/react"
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
import { optionOf, playerRow, scoreField } from "@/test/playForm"
import { page } from "@/test/queries"
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

  await userEvent.selectOptions(await page.findByTestId("players-to-add"), "Anna")
  await userEvent.selectOptions(page.getByTestId("players-to-add"), "Dora")
  await userEvent.selectOptions(playerRow("Bence").getByTestId("player-setup-wonder"), "Babylon")
  await userEvent.selectOptions(playerRow("Bence").getByTestId("player-setup-side"), "Night")
  await userEvent.selectOptions(playerRow("Anna").getByTestId("player-setup-wonder"), "Giza")

  // Babylon is Bence's, so nobody else can have it.
  const doraWonder = playerRow("Dora").getByTestId("player-setup-wonder")
  expect(optionOf(doraWonder, "babylon")).toBeDisabled()
  expect(optionOf(doraWonder, "rhodes")).toBeEnabled()

  for (const [name, wonder, military] of [
    ["Bence", "10", "9"],
    ["Anna", "7", "-2"],
    ["Dora", "3", "0"],
  ]) {
    await userEvent.type(scoreField(name, "wonder"), wonder)
    await userEvent.type(scoreField(name, "military"), military)
  }
  await userEvent.click(page.getByTestId("save-play"))

  await page.findByTestIdWith("page-title", "7 Wonders")
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

  expect(await page.findByTestIdWith("player-setup", "Babylon · Night")).toBeInTheDocument()
  expect(page.getByTestIdWith("player-setup", "Giza")).toBeInTheDocument()
})

test("editing a play keeps each player's choices", async () => {
  signedInAs(bence)
  gamesAre([sevenWonders])
  playCanBeEntered()
  playIs(wondersPlay())
  renderApp(`/plays/${wondersPlay().id}/edit`)

  await waitFor(() =>
    expect(playerRow("Bence").getByTestId("player-setup-wonder")).toHaveValue("babylon"),
  )
  expect(playerRow("Bence").getByTestId("player-setup-side")).toHaveValue("night")
  expect(playerRow("Anna").getByTestId("player-setup-wonder")).toHaveValue("giza")
  expect(playerRow("Dora").getByTestId("player-setup-wonder")).toHaveValue("")
})

test("a wonder that comes with an expansion is offered once the expansion is ticked", async () => {
  signedInAs(bence)
  gamesAre([sevenWonders])
  playCanBeEntered()
  playsCanBePreviewed()
  renderApp(`/plays/new?game=${sevenWonders.id}`)

  // The Expansions come before the Players: they decide what can be chosen for each Player.
  const expansions = await page.findByTestId("expansions")
  const players = page.getByTestId("players")
  expect(expansions.compareDocumentPosition(players)).toBe(Node.DOCUMENT_POSITION_FOLLOWING)

  const wonder = playerRow("Bence").getByTestId("player-setup-wonder")
  expect(optionOf(wonder, "petra")).toBeUndefined()
  await userEvent.click(page.getByTestId("expansion-cities"))
  await userEvent.selectOptions(wonder, "Petra")

  // Taking Cities off again keeps Petra, and says at once why it can't stay.
  await userEvent.click(page.getByTestId("expansion-cities"))
  expect(wonder).toHaveValue("petra")
  expect(
    page.getByTestIdWith(
      "field-error",
      "This Wonder board comes with Cities. Tick it under Expansions, or choose another.",
    ),
  ).toBeInTheDocument()
  await userEvent.selectOptions(wonder, "Giza")
  expect(page.queryByTestIdWith("field-error", /comes with Cities/)).not.toBeInTheDocument()
  expect(optionOf(wonder, "petra")).toBeUndefined()
})
