import { waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { expect, test } from "vitest"
import type { Game } from "@/api/client"
import { anna, bence, azul, gamesAre, pandemic, problem, signedInAs } from "@/test/api"
import { inside, page } from "@/test/queries"
import { renderApp } from "@/test/renderApp"
import { server } from "@/test/server"

function rowOf(name: string) {
  return inside(page.getByTestIdWith("game-row", name))
}

async function openActionsFor(name: string) {
  await userEvent.click(
    await inside(await page.findByTestIdWith("game-row", name)).findByTestId("game-actions"),
  )
}

/** Answers "add a game" by adding what was sent to the list, and remembers what was sent. */
function gamesCanBeAdded(state: { games: Game[] }) {
  const sent: unknown[] = []
  server.use(
    http.post("*/api/games", async ({ request }) => {
      const body = (await request.json()) as Omit<Game, "id" | "gameModuleId">
      sent.push(body)
      const created = { ...azul, id: "0193a000-0000-7000-8000-0000000000e9", ...body }
      state.games.push(created)
      return HttpResponse.json(created)
    }),
  )
  return sent
}

test("lists the games and how their plays are recorded", async () => {
  signedInAs(anna)
  gamesAre([
    azul,
    pandemic,
    { ...azul, id: "0193a000-0000-7000-8000-0000000000e3", name: "Golf", lowestScoreWins: true },
  ])

  renderApp("/")

  await page.findByTestIdWith("game-row", "Azul")
  expect(rowOf("Azul").getByTestId("recorded-as")).toHaveTextContent("Points, highest wins")
  expect(rowOf("Golf").getByTestId("recorded-as")).toHaveTextContent("Points, lowest wins")
  expect(rowOf("Pandemic").getByTestId("recorded-as")).toHaveTextContent("Cooperative, won or lost")
})

test("says so when there are no games yet, or when they can't be loaded", async () => {
  signedInAs(anna)
  gamesAre([])
  const { unmount } = renderApp("/")
  expect(await page.findByTestId("no-games")).toHaveTextContent("No games yet.")
  unmount()

  server.use(http.get("*/api/games", () => problem(500, "unexpected")))
  const { queryClient } = await import("@/api/queryClient")
  queryClient.clear()
  renderApp("/")
  expect(await page.findByTestId("load-failed")).toHaveTextContent("Couldn't load the games")
})

test("a member can add and edit games, but not remove them", async () => {
  signedInAs(bence)
  gamesAre([azul])
  renderApp("/")

  expect(await page.findByTestId("add-game")).toBeInTheDocument()
  await openActionsFor("Azul")
  expect(await page.findByTestId("edit-game")).toBeInTheDocument()
  expect(page.queryByTestId("remove-game")).not.toBeInTheDocument()
})

test("adds a game that is recorded in points, highest wins, unless told otherwise", async () => {
  signedInAs(bence)
  const sent = gamesCanBeAdded(gamesAre([]))
  renderApp("/")

  await userEvent.click(await page.findByTestId("add-game"))
  expect(await page.findByTestId("game-dialog")).toHaveAccessibleName("Add a game")
  await userEvent.type(page.getByTestId("game-name"), "Cascadia")
  await userEvent.click(page.getByTestId("save-game"))

  expect(await page.findByTestIdWith("game-row", "Cascadia")).toBeInTheDocument()
  expect(sent).toEqual([
    { name: "Cascadia", cooperative: false, recordingMode: "POINTS", lowestScoreWins: false },
  ])
  // The keyboard carries on from the button, not from the top of the page.
  await waitFor(() => expect(page.getByTestId("add-game")).toHaveFocus())
})

test("cancelling the dialog for a new game gives focus back to its button", async () => {
  signedInAs(bence)
  gamesAre([])
  renderApp("/")

  const add = await page.findByTestId("add-game")
  add.focus()
  await userEvent.keyboard("{Enter}")
  await page.findByTestId("game-dialog")
  await userEvent.keyboard("{Escape}")

  await waitFor(() => expect(add).toHaveFocus())
})

test("adds a game where the lowest score wins", async () => {
  signedInAs(bence)
  const sent = gamesCanBeAdded(gamesAre([]))
  renderApp("/")

  await userEvent.click(await page.findByTestId("add-game"))
  await userEvent.type(await page.findByTestId("game-name"), "Golf")
  await userEvent.click(page.getByTestId("recording-winner-lowest"))
  await userEvent.click(page.getByTestId("save-game"))

  expect(
    await inside(await page.findByTestIdWith("game-row", "Golf")).findByTestId("recorded-as"),
  ).toHaveTextContent("Points, lowest wins")
  expect(sent).toEqual([
    { name: "Golf", cooperative: false, recordingMode: "POINTS", lowestScoreWins: true },
  ])
})

test("a cooperative game is won or lost, and nobody is asked who wins", async () => {
  signedInAs(bence)
  const sent = gamesCanBeAdded(gamesAre([]))
  renderApp("/")

  await userEvent.click(await page.findByTestId("add-game"))
  await userEvent.type(await page.findByTestId("game-name"), "Pandemic")
  expect(page.getByTestId("recording-winner")).toHaveTextContent("Who wins")
  await userEvent.click(page.getByTestId("recording-kind-cooperative"))
  expect(page.queryByTestId("recording-winner")).not.toBeInTheDocument()
  expect(page.getByTestId("recording-mode")).toHaveTextContent("Won or lost only")
  await userEvent.click(page.getByTestId("recording-mode-WINNER_ONLY"))
  await userEvent.click(page.getByTestId("save-game"))

  await page.findByTestIdWith("game-row", "Pandemic")
  expect(sent).toEqual([
    { name: "Pandemic", cooperative: true, recordingMode: "WINNER_ONLY", lowestScoreWins: false },
  ])
})

test("edits a game, starting from what it is now", async () => {
  signedInAs(bence)
  const state = gamesAre([pandemic])
  let sent: unknown
  server.use(
    http.put("*/api/games/:id", async ({ request, params }) => {
      const body = (await request.json()) as Omit<Game, "id" | "gameModuleId">
      sent = { id: params.id, body }
      state.games = [{ ...pandemic, ...body }]
      return HttpResponse.json(state.games[0])
    }),
  )
  renderApp("/")

  await openActionsFor("Pandemic")
  await userEvent.click(await page.findByTestId("edit-game"))
  expect(await page.findByTestId("game-dialog")).toHaveAccessibleName("Edit Pandemic")
  expect(page.getByTestId("game-name")).toHaveValue("Pandemic")
  expect(page.getByTestId("recording-kind-cooperative")).toBeChecked()
  expect(page.getByTestId("recording-mode-WINNER_ONLY")).toBeChecked()
  await userEvent.click(page.getByTestId("recording-mode-POINTS"))
  await userEvent.click(page.getByTestId("save-game"))

  await waitFor(() =>
    expect(rowOf("Pandemic").getByTestId("recorded-as")).toHaveTextContent(
      "Cooperative, with a score",
    ),
  )
  expect(sent).toEqual({
    id: pandemic.id,
    body: { name: "Pandemic", cooperative: true, recordingMode: "POINTS", lowestScoreWins: false },
  })
})

test("says so when a game's name is taken, and keeps the dialog open", async () => {
  signedInAs(bence)
  gamesAre([azul])
  server.use(http.post("*/api/games", () => problem(409, "games.nameTaken")))
  renderApp("/")

  await userEvent.click(await page.findByTestId("add-game"))
  await userEvent.type(await page.findByTestId("game-name"), "azul")
  await userEvent.click(page.getByTestId("save-game"))

  expect(await page.findByTestId("problem")).toHaveTextContent(
    "There is already a game with this name.",
  )
  expect(page.getByTestId("game-dialog")).toBeInTheDocument()
})

test("an admin removes a game only after being asked", async () => {
  signedInAs(anna)
  const state = gamesAre([azul, pandemic])
  server.use(
    http.delete("*/api/games/:id", ({ params }) => {
      state.games = state.games.filter((game) => game.id !== params.id)
      return new HttpResponse(null, { status: 204 })
    }),
  )
  renderApp("/")

  await openActionsFor("Azul")
  await userEvent.click(await page.findByTestId("remove-game"))
  expect(await page.findByTestId("confirm-dialog")).toHaveAccessibleName("Remove Azul?")
  expect(state.games).toHaveLength(2)
  await userEvent.click(page.getByTestId("confirm"))

  await expect.poll(() => page.queryByTestIdWith("game-row", "Azul")).not.toBeInTheDocument()
  expect(page.getByTestIdWith("game-row", "Pandemic")).toBeInTheDocument()
})

test("shows a not-found page for an unknown address", async () => {
  signedInAs(anna)

  renderApp("/no-such-page")

  expect(await page.findByTestIdWith("page-title", "Page not found")).toBeInTheDocument()
})
