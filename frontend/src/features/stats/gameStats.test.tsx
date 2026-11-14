import { screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { expect, test } from "vitest"
import type { GameStats } from "@/api/client"
import {
  annaPlayer,
  azul,
  azulPlay,
  bence,
  bencePlayer,
  calico,
  doraPlayer,
  duel,
  gamesAre,
  pandemic,
  playIs,
  problem,
  signedInAs,
  wyrmspan,
} from "@/test/api"
import { calicoModule, duelModule, genericModule, wyrmspanModule } from "@/test/gameModules"
import { renderApp } from "@/test/renderApp"
import { server } from "@/test/server"

function statsAre(stats: GameStats) {
  server.use(http.get(`*/api/stats/games/${stats.gameId}`, () => HttpResponse.json(stats)))
}

/** Statistics of a Game nobody has played yet, for a test to fill in. */
function noStats(gameId: string, gameModuleId: string): GameStats {
  return {
    gameId,
    gameModuleId,
    plays: 0,
    players: [],
    categoryAverages: {},
    solo: [],
    victoryTypes: [],
  }
}

/** The cells of each row of a table, as text, below its heading row. */
function rowsOf(table: HTMLElement) {
  return within(table)
    .getAllByRole("row")
    .slice(1)
    .map((row) =>
      within(row)
        .getAllByRole("cell")
        .map((cell) => cell.textContent),
    )
}

test("shows how each player did against the others", async () => {
  signedInAs(bence)
  gamesAre([azul])
  statsAre({
    ...noStats(azul.id, genericModule.id),
    plays: 3,
    lastPlayedOn: "2026-03-15",
    players: [
      {
        playerId: doraPlayer.id,
        name: "Dora",
        plays: 3,
        wins: 2,
        bestScore: 45,
        averageScore: 42.5,
        averagePlacing: 1.5,
      },
      { playerId: annaPlayer.id, name: "Anna", plays: 3, wins: 1, bestScore: 50, averageScore: 40 },
    ],
  })

  renderApp(`/games/${azul.id}`)

  expect(await screen.findByRole("heading", { name: "Azul" })).toBeInTheDocument()
  expect(screen.getByText("Played 3 times · Last played 15 March 2026")).toBeInTheDocument()
  const table = screen.getByRole("table", { name: "Players" })
  expect(rowsOf(table)).toEqual([
    ["Dora", "2 of 367%", "45", "42.5", "1.5"],
    // Anna's Placings were never recorded: the column shows a dash.
    ["Anna", "1 of 333%", "50", "40", "–"],
  ])
  expect(screen.getByRole("link", { name: "All plays of Azul" })).toHaveAttribute(
    "href",
    `/plays?game=${azul.id}`,
  )
  expect(screen.getByRole("link", { name: "Record a play" })).toHaveAttribute(
    "href",
    `/plays/new?game=${azul.id}`,
  )
})

test("leaves out the score columns when only winners were recorded", async () => {
  signedInAs(bence)
  gamesAre([azul])
  statsAre({
    ...noStats(azul.id, genericModule.id),
    plays: 1,
    lastPlayedOn: "2026-03-15",
    players: [
      { playerId: doraPlayer.id, name: "Dora", plays: 1, wins: 1 },
      { playerId: annaPlayer.id, name: "Anna", plays: 1, wins: 0 },
    ],
  })

  renderApp(`/games/${azul.id}`)

  const table = await screen.findByRole("table", { name: "Players" })
  expect(
    within(table)
      .getAllByRole("columnheader")
      .map((head) => head.textContent),
  ).toEqual(["Player", "Wins"])
})

test("shows the average of each row of the score pad, in the pad's order", async () => {
  signedInAs(bence)
  gamesAre([calico])
  statsAre({
    ...noStats(calico.id, calicoModule.id),
    plays: 1,
    lastPlayedOn: "2026-03-15",
    players: [
      { playerId: annaPlayer.id, name: "Anna", plays: 1, wins: 1, bestScore: 72, averageScore: 72 },
    ],
    categoryAverages: { buttons: 19.5, cats: 21, designGoals: 24.25 },
  })

  renderApp(`/games/${calico.id}`)

  const table = await screen.findByRole("table", { name: "Score pad averages" })
  expect(
    within(table)
      .getAllByRole("row")
      .map((row) => row.textContent),
  ).toEqual(["Design goals24.3", "Cats21", "Buttons19.5"])
})

test("shows how the plays of a game won in several ways were won", async () => {
  signedInAs(bence)
  gamesAre([duel])
  statsAre({
    ...noStats(duel.id, duelModule.id),
    plays: 3,
    lastPlayedOn: "2026-03-15",
    players: [{ playerId: annaPlayer.id, name: "Anna", plays: 3, wins: 2 }],
    victoryTypes: [
      { key: "militarySupremacy", plays: 2 },
      { key: "civilian", plays: 1 },
    ],
  })

  renderApp(`/games/${duel.id}`)

  const table = await screen.findByRole("table", { name: "How plays were won" })
  expect(
    within(table)
      .getAllByRole("row")
      .map((row) => row.textContent),
  ).toEqual(["Won byPlays", "Military supremacy2", "Civilian victory (on points)1"])
})

test("a game won in one way only doesn't say how its plays were won", async () => {
  signedInAs(bence)
  gamesAre([calico])
  statsAre({
    ...noStats(calico.id, calicoModule.id),
    plays: 1,
    players: [{ playerId: annaPlayer.id, name: "Anna", plays: 1, wins: 1 }],
  })

  renderApp(`/games/${calico.id}`)

  await screen.findByRole("table", { name: "Players" })
  expect(screen.queryByRole("table", { name: "How plays were won" })).not.toBeInTheDocument()
})

test("shows solo plays in a section of their own", async () => {
  signedInAs(bence)
  gamesAre([wyrmspan])
  statsAre({
    ...noStats(wyrmspan.id, wyrmspanModule.id),
    plays: 2,
    lastPlayedOn: "2026-03-15",
    solo: [
      {
        playerId: bencePlayer.id,
        name: "Bence",
        plays: 2,
        wins: 1,
        playsAgainstOpponent: 2,
        bestScore: 93,
        averageScore: 88.5,
      },
    ],
  })

  renderApp(`/games/${wyrmspan.id}`)

  const table = await screen.findByRole("table", { name: "Solo plays" })
  expect(rowsOf(table)).toEqual([["Bence", "2", "1 of 2", "93", "88.5"]])
  expect(screen.queryByRole("table", { name: "Players" })).not.toBeInTheDocument()
})

test("shows how the group did in cooperative plays", async () => {
  signedInAs(bence)
  gamesAre([pandemic])
  statsAre({
    ...noStats(pandemic.id, genericModule.id),
    plays: 2,
    lastPlayedOn: "2026-03-15",
    cooperative: { plays: 2, wins: 1 },
  })

  renderApp(`/games/${pandemic.id}`)

  expect(await screen.findByRole("heading", { name: "Played together" })).toBeInTheDocument()
  expect(screen.getByText("Won").nextElementSibling).toHaveTextContent("1 of 2 (50%)")
})

test("says when a game has not been played yet", async () => {
  signedInAs(bence)
  gamesAre([azul])
  statsAre(noStats(azul.id, genericModule.id))

  renderApp(`/games/${azul.id}`)

  expect(await screen.findByText("Not played yet")).toBeInTheDocument()
  expect(
    screen.getByText("No plays of this game yet. Its statistics start with the first one."),
  ).toBeInTheDocument()
})

test("says so when the game no longer exists", async () => {
  signedInAs(bence)
  gamesAre([])
  server.use(http.get("*/api/stats/games/:id", () => problem(404, "games.notFound")))

  renderApp(`/games/${azul.id}`)

  expect(
    await screen.findByRole("heading", { name: "This game no longer exists." }),
  ).toBeInTheDocument()
})

test("each game's menu on the start page leads to its statistics", async () => {
  signedInAs(bence)
  gamesAre([calico])
  statsAre(noStats(calico.id, calicoModule.id))

  renderApp("/")

  await userEvent.click(await screen.findByRole("button", { name: "Actions for Calico" }))
  // Calico has a module of its own: nothing to edit or remove, but statistics all the same.
  expect(screen.queryByRole("menuitem", { name: "Edit" })).not.toBeInTheDocument()
  await userEvent.click(await screen.findByRole("menuitem", { name: "Statistics" }))

  expect(await screen.findByText("Not played yet")).toBeInTheDocument()
})

test("the game's name on a play's page leads to its statistics", async () => {
  signedInAs(bence)
  gamesAre([azul])
  playIs(azulPlay)
  statsAre(noStats(azul.id, genericModule.id))

  renderApp(`/plays/${azulPlay.id}`)

  await userEvent.click(await screen.findByRole("link", { name: "Azul" }))

  expect(await screen.findByText("Not played yet")).toBeInTheDocument()
})
