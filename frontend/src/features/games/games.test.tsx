import { screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { expect, test } from "vitest"
import type { Game } from "@/api/client"
import { anna, bence, azul, gamesAre, pandemic, problem, signedInAs } from "@/test/api"
import { renderApp } from "@/test/renderApp"
import { server } from "@/test/server"

function rowOf(name: string) {
  return screen.getByRole("row", { name: new RegExp(name) })
}

async function openActionsFor(name: string) {
  await userEvent.click(await screen.findByRole("button", { name: `Actions for ${name}` }))
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

  await screen.findByRole("row", { name: /Azul/ })
  expect(within(rowOf("Azul")).getByText("Points, highest wins")).toBeInTheDocument()
  expect(within(rowOf("Golf")).getByText("Points, lowest wins")).toBeInTheDocument()
  expect(within(rowOf("Pandemic")).getByText("Cooperative, won or lost")).toBeInTheDocument()
})

test("says so when there are no games yet, or when they can't be loaded", async () => {
  signedInAs(anna)
  gamesAre([])
  const { unmount } = renderApp("/")
  expect(await screen.findByText("No games yet.")).toBeInTheDocument()
  unmount()

  server.use(http.get("*/api/games", () => problem(500, "unexpected")))
  const { queryClient } = await import("@/api/queryClient")
  queryClient.clear()
  renderApp("/")
  expect(await screen.findByText("Couldn't load the games")).toBeInTheDocument()
})

test("a member can add and edit games, but not remove them", async () => {
  signedInAs(bence)
  gamesAre([azul])
  renderApp("/")

  expect(await screen.findByRole("button", { name: "Add game" })).toBeInTheDocument()
  await openActionsFor("Azul")
  expect(await screen.findByRole("menuitem", { name: "Edit" })).toBeInTheDocument()
  expect(screen.queryByRole("menuitem", { name: "Remove" })).not.toBeInTheDocument()
})

test("adds a game that is recorded in points, highest wins, unless told otherwise", async () => {
  signedInAs(bence)
  const sent = gamesCanBeAdded(gamesAre([]))
  renderApp("/")

  await userEvent.click(await screen.findByRole("button", { name: "Add game" }))
  const dialog = await screen.findByRole("dialog", { name: "Add a game" })
  await userEvent.type(within(dialog).getByLabelText("Name"), "Cascadia")
  await userEvent.click(within(dialog).getByRole("button", { name: "Add game" }))

  expect(await screen.findByRole("row", { name: /Cascadia/ })).toBeInTheDocument()
  expect(sent).toEqual([
    { name: "Cascadia", cooperative: false, recordingMode: "POINTS", lowestScoreWins: false },
  ])
  // The keyboard carries on from the button, not from the top of the page.
  await waitFor(() => expect(screen.getByRole("button", { name: "Add game" })).toHaveFocus())
})

test("cancelling the dialog for a new game gives focus back to its button", async () => {
  signedInAs(bence)
  gamesAre([])
  renderApp("/")

  const add = await screen.findByRole("button", { name: "Add game" })
  add.focus()
  await userEvent.keyboard("{Enter}")
  await screen.findByRole("dialog", { name: "Add a game" })
  await userEvent.keyboard("{Escape}")

  await waitFor(() => expect(add).toHaveFocus())
})

test("adds a game where the lowest score wins", async () => {
  signedInAs(bence)
  const sent = gamesCanBeAdded(gamesAre([]))
  renderApp("/")

  await userEvent.click(await screen.findByRole("button", { name: "Add game" }))
  const dialog = await screen.findByRole("dialog")
  await userEvent.type(within(dialog).getByLabelText("Name"), "Golf")
  await userEvent.click(within(dialog).getByRole("radio", { name: "The lowest score" }))
  await userEvent.click(within(dialog).getByRole("button", { name: "Add game" }))

  expect(
    await within(await screen.findByRole("row", { name: /Golf/ })).findByText(
      "Points, lowest wins",
    ),
  ).toBeInTheDocument()
  expect(sent).toEqual([
    { name: "Golf", cooperative: false, recordingMode: "POINTS", lowestScoreWins: true },
  ])
})

test("a cooperative game is won or lost, and nobody is asked who wins", async () => {
  signedInAs(bence)
  const sent = gamesCanBeAdded(gamesAre([]))
  renderApp("/")

  await userEvent.click(await screen.findByRole("button", { name: "Add game" }))
  const dialog = await screen.findByRole("dialog")
  await userEvent.type(within(dialog).getByLabelText("Name"), "Pandemic")
  expect(within(dialog).getByText("Who wins")).toBeInTheDocument()
  await userEvent.click(within(dialog).getByRole("radio", { name: "Cooperative" }))
  expect(within(dialog).queryByText("Who wins")).not.toBeInTheDocument()
  await userEvent.click(within(dialog).getByRole("radio", { name: "Won or lost only" }))
  await userEvent.click(within(dialog).getByRole("button", { name: "Add game" }))

  await screen.findByRole("row", { name: /Pandemic/ })
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
  await userEvent.click(await screen.findByRole("menuitem", { name: "Edit" }))
  const dialog = await screen.findByRole("dialog", { name: "Edit Pandemic" })
  expect(within(dialog).getByLabelText("Name")).toHaveValue("Pandemic")
  expect(within(dialog).getByRole("radio", { name: "Cooperative" })).toBeChecked()
  expect(within(dialog).getByRole("radio", { name: "Won or lost only" })).toBeChecked()
  await userEvent.click(within(dialog).getByRole("radio", { name: "Points" }))
  await userEvent.click(within(dialog).getByRole("button", { name: "Save game" }))

  expect(
    await within(rowOf("Pandemic")).findByText("Cooperative, with a score"),
  ).toBeInTheDocument()
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

  await userEvent.click(await screen.findByRole("button", { name: "Add game" }))
  const dialog = await screen.findByRole("dialog")
  await userEvent.type(within(dialog).getByLabelText("Name"), "azul")
  await userEvent.click(within(dialog).getByRole("button", { name: "Add game" }))

  expect(await screen.findByText("There is already a game with this name.")).toBeInTheDocument()
  expect(screen.getByRole("dialog")).toBeInTheDocument()
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
  await userEvent.click(await screen.findByRole("menuitem", { name: "Remove" }))
  expect(await screen.findByRole("alertdialog", { name: "Remove Azul?" })).toBeInTheDocument()
  expect(state.games).toHaveLength(2)
  await userEvent.click(screen.getByRole("button", { name: "Remove game" }))

  await expect.poll(() => screen.queryByRole("row", { name: /Azul/ })).not.toBeInTheDocument()
  expect(screen.getByRole("row", { name: /Pandemic/ })).toBeInTheDocument()
})

test("shows a not-found page for an unknown address", async () => {
  signedInAs(anna)

  renderApp("/no-such-page")

  expect(await screen.findByRole("heading", { name: "Page not found" })).toBeInTheDocument()
})
