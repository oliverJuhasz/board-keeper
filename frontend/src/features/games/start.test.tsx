import { screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { expect, test } from "vitest"
import type { Play } from "@/api/client"
import { todayIso } from "@/lib/dates"
import {
  annaPlayer,
  azul,
  azulPlay,
  bence,
  bencePlayer,
  calico,
  doraPlayer,
  gamesAre,
  pandemic,
  pandemicPlay,
  playCanBeEntered,
  playIs,
  playsCanBePreviewed,
  signedInAs,
} from "@/test/api"
import { calicoModule } from "@/test/gameModules"
import { renderApp } from "@/test/renderApp"
import { server } from "@/test/server"

/** The day a number of days before today, as the API writes days. */
function daysAgo(days: number) {
  const day = new Date(`${todayIso()}T00:00:00Z`)
  day.setUTCDate(day.getUTCDate() - days)
  return day.toISOString().slice(0, 10)
}

function lastPlayedAre(entries: Record<string, string>) {
  server.use(
    http.get("*/api/plays/last-played", () =>
      HttpResponse.json(
        Object.entries(entries).map(([gameId, playedOn]) => ({ gameId, playedOn })),
      ),
    ),
  )
}

/** The recent Plays the start page offers to play again, newest first. */
function recentPlaysAre(plays: Play[]) {
  const asked: URLSearchParams[] = []
  server.use(
    http.get("*/api/plays", ({ request }) => {
      asked.push(new URL(request.url).searchParams)
      return HttpResponse.json({ plays })
    }),
  )
  return asked
}

/** A Play of Calico, the Game with its own module, played in the family variant. */
const calicoPlay: Play = {
  ...azulPlay,
  id: "0193a000-0000-7000-8000-0000000000f9",
  game: { id: calico.id, name: calico.name, moduleKey: "calico" },
  gameModuleId: calicoModule.id,
  playedOn: daysAgo(1),
  setup: { family: "true" },
  results: [
    {
      player: doraPlayer,
      scores: { cats: 20, buttons: 9 },
      markedWinner: false,
      total: 29,
      placing: 2,
      won: false,
    },
    {
      player: bencePlayer,
      scores: { cats: 25, buttons: 12 },
      markedWinner: false,
      total: 37,
      placing: 1,
      won: true,
    },
  ],
}

function gameRows() {
  return screen
    .getAllByRole("row")
    .slice(1)
    .map((row) => row.textContent)
}

test("lists the games most recently played first, and says when", async () => {
  signedInAs(bence)
  gamesAre([azul, calico, pandemic])
  lastPlayedAre({ [azul.id]: daysAgo(30), [calico.id]: daysAgo(1) })
  renderApp("/")

  expect(await screen.findByRole("heading", { name: "Record a play" })).toBeInTheDocument()
  await waitFor(() =>
    expect(gameRows()).toEqual([
      expect.stringMatching(/^CalicoLast played yesterday/),
      expect.stringMatching(/^AzulLast played \d+ \w+ \d{4}/),
      expect.stringMatching(/^PandemicNot played yet/),
    ]),
  )
})

test("a tap on a game opens the form for a play of it", async () => {
  signedInAs(bence)
  gamesAre([calico])
  playCanBeEntered()
  renderApp("/")

  await userEvent.click(await screen.findByRole("link", { name: "Record a play of Calico" }))

  // The start page has the same heading, and stays until the form's code has loaded: wait for
  // what only the form says.
  expect(await screen.findByText("Who played Calico, and how it ended.")).toBeInTheDocument()
  expect(screen.getByRole("heading", { name: "Record a play" })).toBeInTheDocument()
})

test("offers my recent plays to play again, each game and group of players once", async () => {
  signedInAs(bence)
  gamesAre([azul, calico, pandemic])
  const earlierCalico = {
    ...calicoPlay,
    id: "0193a000-0000-7000-8000-0000000000fa",
    playedOn: daysAgo(5),
  }
  const asked = recentPlaysAre([calicoPlay, earlierCalico, azulPlay, pandemicPlay])
  renderApp("/")

  const section = await screen.findByRole("heading", { name: "Play again" })
  const offers = within(section.parentElement as HTMLElement).getAllByRole("link")
  expect(offers.map((link) => link.getAttribute("aria-label"))).toEqual([
    "Play Calico again with Dora, Bence",
    "Play Azul again with Dora, Anna",
    "Play Pandemic again with Anna, Bence",
  ])
  expect(offers[0]).toHaveTextContent("yesterday")
  // Only plays the signed-in player took part in.
  expect(asked[0].getAll("playerId")).toEqual([bencePlayer.id])
})

test("playing again fills in the same players and choices", async () => {
  signedInAs(bence)
  gamesAre([calico])
  recentPlaysAre([calicoPlay])
  playIs(calicoPlay)
  playCanBeEntered()
  playsCanBePreviewed()
  renderApp("/")

  await userEvent.click(
    await screen.findByRole("link", { name: "Play Calico again with Dora, Bence" }),
  )

  // The same players in the same order, in the family variant, and only the scores to enter.
  expect(await screen.findByLabelText("Cats of Dora")).toHaveValue(null)
  expect(screen.getByLabelText("Cats of Bence")).toBeInTheDocument()
  expect(screen.queryByLabelText("Design goals of Dora")).not.toBeInTheDocument()
  expect(screen.getByRole("checkbox", { name: "Family variant" })).toBeChecked()
  const pad = within(screen.getByRole("table", { name: "Score pad" }))
  expect(pad.getAllByRole("columnheader").map((cell) => cell.textContent)).toEqual([
    "Dora",
    "Bence",
  ])
  expect(screen.getByLabelText("Date")).toHaveValue(todayIso())
})

test("a play of a game no longer in the catalogue is not offered", async () => {
  signedInAs(bence)
  gamesAre([azul])
  recentPlaysAre([calicoPlay, azulPlay])
  renderApp("/")

  const section = await screen.findByRole("heading", { name: "Play again" })
  expect(within(section.parentElement as HTMLElement).getAllByRole("link")).toHaveLength(1)
})

test("a player who has been removed is left out when playing again", async () => {
  signedInAs(bence)
  gamesAre([azul])
  playIs(azulPlay)
  // Dora is no longer a Player.
  server.use(http.get("*/api/players", () => HttpResponse.json([annaPlayer, bencePlayer])))
  playsCanBePreviewed()
  renderApp(`/plays/new?game=${azul.id}&again=${azulPlay.id}`)

  expect(await screen.findByLabelText("Score of Anna")).toBeInTheDocument()
  expect(screen.queryByLabelText("Score of Dora")).not.toBeInTheDocument()
})

test("someone who may not record plays sees the catalogue only", async () => {
  signedInAs({ ...bence, permissions: ["CREATE_GAMES"] })
  gamesAre([azul])
  const asked = recentPlaysAre([azulPlay])
  renderApp("/")

  expect(await screen.findByRole("heading", { name: "Games" })).toBeInTheDocument()
  await screen.findByRole("row", { name: /Azul/ })
  expect(screen.queryByRole("heading", { name: "Play again" })).not.toBeInTheDocument()
  expect(screen.queryByRole("link", { name: "Record a play of Azul" })).not.toBeInTheDocument()
  expect(asked).toEqual([])
})
