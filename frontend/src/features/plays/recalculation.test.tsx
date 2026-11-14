import { screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { expect, test } from "vitest"
import type { GameRecalculation, GameStats } from "@/api/client"
import { anna, azul, azulPlay, bence, gamesAre, playIs, problem, signedInAs } from "@/test/api"
import { genericModule } from "@/test/gameModules"
import { renderApp } from "@/test/renderApp"
import { server } from "@/test/server"

// Recalculation (ADR 0010): running the Game Module over what was entered for a Play again,
// for one Play by whoever may change it, and for every Play of a Game by an Admin.

function recalculationAnswers(changed: boolean, total: number) {
  const recalculated = {
    ...azulPlay,
    results: azulPlay.results.map((result, index) => (index === 0 ? { ...result, total } : result)),
  }
  server.use(
    http.post(`*/api/plays/${azulPlay.id}/recalculation`, () =>
      HttpResponse.json({ changed, play: recalculated }),
    ),
  )
}

async function recalculateFromTheMenu() {
  await userEvent.click(await screen.findByRole("button", { name: "Actions for this play" }))
  await userEvent.click(await screen.findByRole("menuitem", { name: "Recalculate" }))
}

test("a play is recalculated from its menu, and says whether its results changed", async () => {
  signedInAs(bence)
  playIs(azulPlay)
  recalculationAnswers(true, 52)
  renderApp(`/plays/${azulPlay.id}`)

  await recalculateFromTheMenu()

  expect(await screen.findByText("Recalculated: the results changed.")).toBeInTheDocument()
  const results = screen.getByRole("table", { name: "Results" })
  expect(within(results).getByText("52")).toBeInTheDocument()
})

test("a play whose results still hold says so", async () => {
  signedInAs(bence)
  playIs(azulPlay)
  recalculationAnswers(false, azulPlay.results[0].total ?? 0)
  renderApp(`/plays/${azulPlay.id}`)

  await recalculateFromTheMenu()

  expect(await screen.findByText("Recalculated: the results are the same.")).toBeInTheDocument()
})

test("a play the game's rules refuse now keeps its results, and says why", async () => {
  signedInAs(bence)
  playIs(azulPlay)
  server.use(
    http.post(`*/api/plays/${azulPlay.id}/recalculation`, () =>
      HttpResponse.json(
        { status: 422, code: "plays.invalidEntry", reason: "plays.invalid.scoreNotAllowed" },
        { status: 422 },
      ),
    ),
  )
  renderApp(`/plays/${azulPlay.id}`)

  await recalculateFromTheMenu()

  expect(
    await screen.findByText(
      "This play kept its results, because the game's rules refuse it now: One of the scores isn't possible in its row of the score pad.",
    ),
  ).toBeInTheDocument()
})

/** Statistics of Azul with three Plays, for the Game's page. */
function statsOfAzul() {
  const stats: GameStats = {
    gameId: azul.id,
    gameModuleId: genericModule.id,
    plays: 3,
    lastPlayedOn: "2026-03-15",
    players: [],
    categoryAverages: {},
    solo: [],
    victoryTypes: [],
  }
  server.use(http.get(`*/api/stats/games/${azul.id}`, () => HttpResponse.json(stats)))
}

test("an admin recalculates every play of a game and reads what changed", async () => {
  signedInAs(anna)
  gamesAre([azul])
  statsOfAzul()
  const report: GameRecalculation = {
    plays: 3,
    changed: 1,
    refused: [
      {
        playId: azulPlay.id,
        playedOn: "2026-03-14",
        reason: "plays.invalid.scoreNotAllowed",
      },
    ],
  }
  let asked: string | null = null
  server.use(
    http.post("*/api/plays/recalculation", ({ request }) => {
      asked = new URL(request.url).searchParams.get("gameId")
      return HttpResponse.json(report)
    }),
  )
  renderApp(`/games/${azul.id}`)

  await userEvent.click(await screen.findByRole("button", { name: "Actions for this game" }))
  await userEvent.click(await screen.findByRole("menuitem", { name: "Recalculate every play" }))

  expect(
    await screen.findByText("Recalculated the game's 3 plays. 1 play got new results."),
  ).toBeInTheDocument()
  expect(
    screen.getByText("This play kept its results, because the game's rules refuse it now:"),
  ).toBeInTheDocument()
  expect(screen.getByRole("link", { name: "14 March 2026" })).toHaveAttribute(
    "href",
    `/plays/${azulPlay.id}`,
  )
  expect(asked).toBe(azul.id)
})

test("only admins can recalculate every play of a game", async () => {
  signedInAs(bence)
  gamesAre([azul])
  statsOfAzul()
  renderApp(`/games/${azul.id}`)

  await screen.findByText("Played 3 times · Last played 15 March 2026")
  expect(screen.queryByRole("button", { name: "Actions for this game" })).not.toBeInTheDocument()
  expect(problem).toBeDefined()
})
