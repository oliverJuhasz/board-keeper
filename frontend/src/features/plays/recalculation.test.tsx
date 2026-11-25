import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { expect, test } from "vitest"
import type { GameRecalculation, GameStats } from "@/api/client"
import { anna, azul, azulPlay, bence, gamesAre, playIs, problem, signedInAs } from "@/test/api"
import { genericModule } from "@/test/gameModules"
import { inside, page } from "@/test/queries"
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
  await userEvent.click(await page.findByTestId("play-actions"))
  await userEvent.click(await page.findByTestId("recalculate-play"))
}

test("a play is recalculated from its menu, and says whether its results changed", async () => {
  signedInAs(bence)
  playIs(azulPlay)
  recalculationAnswers(true, 52)
  renderApp(`/plays/${azulPlay.id}`)

  await recalculateFromTheMenu()

  expect(await page.findByTestId("recalculated")).toHaveTextContent(
    "Recalculated: the results changed.",
  )
  expect(page.getAllByTestId("total").map((total) => total.textContent)).toContain("52")
})

test("a play whose results still hold says so", async () => {
  signedInAs(bence)
  playIs(azulPlay)
  recalculationAnswers(false, azulPlay.results[0].total ?? 0)
  renderApp(`/plays/${azulPlay.id}`)

  await recalculateFromTheMenu()

  expect(await page.findByTestId("recalculated")).toHaveTextContent(
    "Recalculated: the results are the same.",
  )
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

  expect(await page.findByTestId("recalculation-refused")).toHaveTextContent(
    "This play kept its results, because the game's rules refuse it now: One of the scores isn't possible in its row of the score pad.",
  )
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

  await userEvent.click(await page.findByTestId("game-actions"))
  await userEvent.click(await page.findByTestId("recalculate-game"))

  const said = await page.findByTestId("recalculation-report")
  expect(said).toHaveTextContent("Recalculated the game's 3 plays. 1 play got new results.")
  expect(said).toHaveTextContent(
    "This play kept its results, because the game's rules refuse it now:",
  )
  const refused = inside(page.getByTestId("refused-play")).getByTestId("play-link")
  expect(refused).toHaveTextContent("14 March 2026")
  expect(refused).toHaveAttribute("href", `/plays/${azulPlay.id}`)
  expect(asked).toBe(azul.id)
})

test("only admins can recalculate every play of a game", async () => {
  signedInAs(bence)
  gamesAre([azul])
  statsOfAzul()
  renderApp(`/games/${azul.id}`)

  await page.findByTestIdWith("page-description", "Played 3 times · Last played 15 March 2026")
  expect(page.queryByTestId("game-actions")).not.toBeInTheDocument()
  expect(problem).toBeDefined()
})
