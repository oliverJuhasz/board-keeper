import { waitFor } from "@testing-library/react"
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
import { findScoreField, padPlayers, queryScoreField, scoreField } from "@/test/playForm"
import { page } from "@/test/queries"
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
      setup: {},
      markedWinner: false,
      total: 29,
      placing: 2,
      won: false,
    },
    {
      player: bencePlayer,
      scores: { cats: 25, buttons: 12 },
      setup: {},
      markedWinner: false,
      total: 37,
      placing: 1,
      won: true,
    },
  ],
}

function gameRows() {
  return page.getAllByTestId("game-row").map((row) => row.textContent)
}

test("lists the games most recently played first, and says when", async () => {
  signedInAs(bence)
  gamesAre([azul, calico, pandemic])
  lastPlayedAre({ [azul.id]: daysAgo(30), [calico.id]: daysAgo(1) })
  renderApp("/")

  expect(await page.findByTestIdWith("page-title", "Record a play")).toBeInTheDocument()
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

  await userEvent.click(await page.findByTestIdWith("record-play", "Calico"))

  // The start page has the same heading, and stays until the form's code has loaded: wait for
  // what only the form says.
  expect(
    await page.findByTestIdWith("page-description", "Who played Calico, and how it ended."),
  ).toBeInTheDocument()
  expect(page.getByTestId("page-title")).toHaveTextContent("Record a play")
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

  await page.findByTestId("play-again")
  const offers = page.getAllByTestId("play-again-offer")
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

  const offer = await page.findByTestIdWith("play-again-offer", "Calico")
  expect(offer).toHaveAccessibleName("Play Calico again with Dora, Bence")
  await userEvent.click(offer)

  // The same players in the same order, in the family variant, and only the scores to enter.
  expect(await findScoreField("Dora", "cats")).toHaveValue(null)
  expect(scoreField("Bence", "cats")).toBeInTheDocument()
  expect(queryScoreField("Dora", "designGoals")).not.toBeInTheDocument()
  expect(page.getByTestId("variant-family")).toBeChecked()
  expect(padPlayers()).toEqual(["Dora", "Bence"])
  expect(page.getByTestId("played-on")).toHaveValue(todayIso())
})

test("a play of a game no longer in the catalogue is not offered", async () => {
  signedInAs(bence)
  gamesAre([azul])
  recentPlaysAre([calicoPlay, azulPlay])
  renderApp("/")

  await page.findByTestId("play-again")
  expect(page.getAllByTestId("play-again-offer")).toHaveLength(1)
})

test("a player who has been removed is left out when playing again", async () => {
  signedInAs(bence)
  gamesAre([azul])
  playIs(azulPlay)
  // Dora is no longer a Player.
  server.use(http.get("*/api/players", () => HttpResponse.json([annaPlayer, bencePlayer])))
  playsCanBePreviewed()
  renderApp(`/plays/new?game=${azul.id}&again=${azulPlay.id}`)

  expect(await findScoreField("Anna")).toBeInTheDocument()
  expect(queryScoreField("Dora")).not.toBeInTheDocument()
})

test("someone who may not record plays sees the catalogue only", async () => {
  signedInAs({ ...bence, permissions: ["CREATE_GAMES"] })
  gamesAre([azul])
  const asked = recentPlaysAre([azulPlay])
  renderApp("/")

  expect(await page.findByTestIdWith("page-title", "Games")).toBeInTheDocument()
  await page.findByTestIdWith("game-row", "Azul")
  expect(page.queryByTestId("play-again")).not.toBeInTheDocument()
  expect(page.queryByTestId("record-play")).not.toBeInTheDocument()
  expect(asked).toEqual([])
})
