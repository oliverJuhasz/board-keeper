import { screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { expect, test } from "vitest"
import type { GameStats, PlayerSummary } from "@/api/client"
import {
  anna,
  annaPlayer,
  azul,
  bence,
  bencePlayer,
  calico,
  doraPlayer,
  gamesAre,
  pandemic,
  playersAre,
  problem,
  signedInAs,
  wyrmspan,
} from "@/test/api"
import { genericModule } from "@/test/gameModules"
import { renderApp } from "@/test/renderApp"
import { server } from "@/test/server"

function statsAre(stats: PlayerSummary) {
  server.use(http.get(`*/api/stats/players/${stats.playerId}`, () => HttpResponse.json(stats)))
}

/** Statistics of a Player who hasn't played yet, for a test to fill in. */
function noStats(playerId: string, name: string): PlayerSummary {
  return { playerId, name, plays: 0, soloPlays: 0, games: [], solo: [] }
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

/** The terms of a description list with their values, as "term: value". */
function termsOf(list: HTMLElement) {
  return Array.from(list.querySelectorAll("dt")).map(
    (term) => `${term.textContent}: ${term.nextElementSibling?.textContent}`,
  )
}

test("shows how a player did, game by game", async () => {
  signedInAs(bence)
  gamesAre([azul, calico, pandemic, wyrmspan])
  statsAre({
    ...noStats(doraPlayer.id, "Dora"),
    plays: 7,
    lastPlayedOn: "2026-03-15",
    againstOthers: { plays: 4, wins: 3, averagePlacing: 1.25 },
    together: { plays: 2, wins: 1 },
    soloPlays: 1,
    games: [
      {
        gameId: azul.id,
        plays: 3,
        wins: 2,
        bestScore: 70,
        averageScore: 61,
        averagePlacing: 1.333,
      },
      { gameId: pandemic.id, plays: 2, wins: 1 },
      { gameId: calico.id, plays: 1, wins: 1, bestScore: 72, averageScore: 72, averagePlacing: 1 },
    ],
    solo: [
      {
        gameId: wyrmspan.id,
        plays: 1,
        wins: 1,
        playsAgainstOpponent: 1,
        bestScore: 93,
        averageScore: 93,
      },
    ],
  })

  renderApp(`/players/${doraPlayer.id}`)

  expect(await screen.findByRole("heading", { name: "Dora" })).toBeInTheDocument()
  expect(screen.getByText("Played 7 times · Last played 15 March 2026")).toBeInTheDocument()
  const summary = screen.getByText("Wins against others").closest("dl")
  expect(summary && termsOf(summary)).toEqual([
    "Wins against others: 3 of 4 (75%)",
    "Average placing: 1.3",
    "Wins together: 1 of 2 (50%)",
    "Solo plays: 1",
  ])
  expect(rowsOf(screen.getByRole("table", { name: "Games" }))).toEqual([
    ["Azul", "2 of 367%", "70", "61", "1.3"],
    ["Pandemic", "1 of 250%", "–", "–", "–"],
    ["Calico", "1 of 1100%", "72", "72", "1"],
  ])
  expect(rowsOf(screen.getByRole("table", { name: "Solo plays" }))).toEqual([
    ["Wyrmspan", "1", "1 of 1", "93", "93"],
  ])
  expect(screen.getByRole("link", { name: "All plays of Dora" })).toHaveAttribute(
    "href",
    `/plays?player=${doraPlayer.id}`,
  )
})

test("each game's name leads to its statistics", async () => {
  signedInAs(bence)
  gamesAre([azul])
  statsAre({
    ...noStats(doraPlayer.id, "Dora"),
    plays: 1,
    lastPlayedOn: "2026-03-15",
    againstOthers: { plays: 1, wins: 1 },
    games: [{ gameId: azul.id, plays: 1, wins: 1 }],
  })
  const azulStats: GameStats = {
    gameId: azul.id,
    gameModuleId: genericModule.id,
    plays: 1,
    lastPlayedOn: "2026-03-15",
    players: [{ playerId: doraPlayer.id, name: "Dora", plays: 1, wins: 1 }],
    categoryAverages: {},
    victoryTypes: [],
    solo: [],
  }
  server.use(http.get(`*/api/stats/games/${azul.id}`, () => HttpResponse.json(azulStats)))

  renderApp(`/players/${doraPlayer.id}`)
  await userEvent.click(await screen.findByRole("link", { name: "Azul" }))

  // And on the game's page, the player's name leads back.
  expect(await screen.findByRole("heading", { name: "Azul" })).toBeInTheDocument()
  await userEvent.click(screen.getByRole("link", { name: "Dora" }))
  expect(await screen.findByRole("heading", { name: "Dora" })).toBeInTheDocument()
})

test("says when a player hasn't played yet", async () => {
  signedInAs(bence)
  gamesAre([])
  statsAre(noStats(bencePlayer.id, "Bence"))

  renderApp(`/players/${bencePlayer.id}`)

  expect(await screen.findByText("Hasn't played yet")).toBeInTheDocument()
  expect(
    screen.getByText("No plays with Bence yet. Their statistics start with the first one."),
  ).toBeInTheDocument()
})

test("says so when the player no longer exists", async () => {
  signedInAs(bence)
  gamesAre([])
  server.use(http.get("*/api/stats/players/:id", () => problem(404, "players.notFound")))

  renderApp(`/players/${doraPlayer.id}`)

  expect(
    await screen.findByRole("heading", { name: "This player no longer exists." }),
  ).toBeInTheDocument()
})

test("each name on the players page leads to the player's statistics", async () => {
  signedInAs(anna)
  playersAre([annaPlayer, doraPlayer], [annaPlayer.id])
  gamesAre([])
  statsAre(noStats(doraPlayer.id, "Dora"))

  renderApp("/players")
  await userEvent.click(await screen.findByRole("link", { name: "Dora" }))

  expect(await screen.findByRole("heading", { name: "Dora" })).toBeInTheDocument()
})
