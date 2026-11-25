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
import { inside, page } from "@/test/queries"
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

/** The cells of a statistics table that the tests read, each with an id of its own. */
const CELL = /^(stats-label|plays|wins|best-score|average-score|average-placing)$/

/** The cells of each row of a statistics table, as text. */
function rowsOf(table: HTMLElement) {
  return inside(table)
    .getAllByTestId("stats-row")
    .map((row) =>
      inside(row)
        .getAllByTestId(CELL)
        .map((cell) => cell.textContent),
    )
}

/** The terms of a description list with their values, as "term: value". */
function termsOf(list: HTMLElement) {
  return Array.from(list.querySelectorAll("dt")).map(
    (term) => `${term.textContent}: ${term.nextElementSibling?.textContent}`,
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

  expect(await page.findByTestIdWith("page-title", "Azul")).toBeInTheDocument()
  expect(page.getByTestId("page-description")).toHaveTextContent(
    "Played 3 times · Last played 15 March 2026",
  )
  const table = page.getByTestId("results-table")
  expect(rowsOf(table)).toEqual([
    ["Dora", "2 of 367%", "45", "42.5", "1.5"],
    // Anna's Placings were never recorded: the column shows a dash.
    ["Anna", "1 of 333%", "50", "40", "–"],
  ])
  expect(page.getByTestId("all-plays")).toHaveTextContent("All plays of Azul")
  expect(page.getByTestId("all-plays")).toHaveAttribute("href", `/plays?game=${azul.id}`)
  expect(page.getByTestId("record-play")).toHaveTextContent("Record a play")
  expect(page.getByTestId("record-play")).toHaveAttribute("href", `/plays/new?game=${azul.id}`)
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

  const table = inside(await page.findByTestId("results-table"))
  expect(table.getByTestId("stats-header")).toHaveTextContent(/^PlayerWins$/)
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

  await page.findByTestId("pad-averages")
  expect(page.getAllByTestId("pad-average").map((row) => row.textContent)).toEqual([
    "Design goals24.3",
    "Cats21",
    "Buttons19.5",
  ])
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

  await page.findByTestId("victory-types")
  expect(page.getByTestId("victory-types-header")).toHaveTextContent("Won byPlays")
  expect(page.getAllByTestId("victory-type-count").map((row) => row.textContent)).toEqual([
    "Military supremacy2",
    "Civilian victory (on points)1",
  ])
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

  await page.findByTestId("results-table")
  expect(page.queryByTestId("victory-types")).not.toBeInTheDocument()
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

  const table = await page.findByTestId("solo-table")
  expect(rowsOf(table)).toEqual([["Bence", "2", "1 of 2", "93", "88.5"]])
  expect(page.queryByTestId("results-table")).not.toBeInTheDocument()
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

  expect(termsOf(await page.findByTestId("together"))).toEqual(["Won: 1 of 2 (50%)"])
})

test("says when a game has not been played yet", async () => {
  signedInAs(bence)
  gamesAre([azul])
  statsAre(noStats(azul.id, genericModule.id))

  renderApp(`/games/${azul.id}`)

  expect(await page.findByTestIdWith("page-description", "Not played yet")).toBeInTheDocument()
  expect(page.getByTestId("no-stats")).toHaveTextContent(
    "No plays of this game yet. Its statistics start with the first one.",
  )
})

test("says so when the game no longer exists", async () => {
  signedInAs(bence)
  gamesAre([])
  server.use(http.get("*/api/stats/games/:id", () => problem(404, "games.notFound")))

  renderApp(`/games/${azul.id}`)

  expect(
    await page.findByTestIdWith("page-title", "This game no longer exists."),
  ).toBeInTheDocument()
})

test("each game's menu on the start page leads to its statistics", async () => {
  signedInAs(bence)
  gamesAre([calico])
  statsAre(noStats(calico.id, calicoModule.id))

  renderApp("/")

  await userEvent.click(
    await inside(await page.findByTestIdWith("game-row", "Calico")).findByTestId("game-actions"),
  )
  // Calico has a module of its own: nothing to edit or remove, but statistics all the same.
  expect(page.queryByTestId("edit-game")).not.toBeInTheDocument()
  await userEvent.click(await page.findByTestId("game-statistics"))

  expect(await page.findByTestIdWith("page-description", "Not played yet")).toBeInTheDocument()
})

test("the game's name on a play's page leads to its statistics", async () => {
  signedInAs(bence)
  gamesAre([azul])
  playIs(azulPlay)
  statsAre(noStats(azul.id, genericModule.id))

  renderApp(`/plays/${azulPlay.id}`)

  await userEvent.click(await page.findByTestId("game-link"))

  expect(await page.findByTestIdWith("page-description", "Not played yet")).toBeInTheDocument()
})
