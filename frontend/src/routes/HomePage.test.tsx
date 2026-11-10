import { screen } from "@testing-library/react"
import { http, HttpResponse } from "msw"
import { expect, test } from "vitest"
import type { Game, GameModule } from "@/api/client"
import { renderApp } from "@/test/renderApp"
import { server } from "@/test/server"

const generic: GameModule = { id: "38b119d2-8f9f-4e20-8068-cba305c06cdd", key: "generic" }

function apiReturns({ games, gameModules }: { games: Game[]; gameModules: GameModule[] }) {
  server.use(
    http.get("*/api/games", () => HttpResponse.json(games)),
    http.get("*/api/game-modules", () => HttpResponse.json(gameModules)),
  )
}

test("shows the installed game modules, and that there are no games yet", async () => {
  apiReturns({ games: [], gameModules: [generic] })

  renderApp("/")

  expect(await screen.findByText("generic")).toBeInTheDocument()
  expect(await screen.findByText("No games yet.")).toBeInTheDocument()
})

test("lists the games in the order the API returns them", async () => {
  apiReturns({
    games: [
      { id: "0193a000-0000-7000-8000-000000000001", name: "Calico", gameModuleId: generic.id },
      { id: "0193a000-0000-7000-8000-000000000002", name: "Wyrmspan", gameModuleId: generic.id },
    ],
    gameModules: [generic],
  })

  renderApp("/")

  await screen.findByText("Calico")
  expect(screen.getAllByRole("listitem").map((item) => item.textContent)).toEqual([
    "Calico",
    "Wyrmspan",
    "generic",
  ])
})

test("says so when the games can't be loaded", async () => {
  server.use(
    http.get("*/api/games", () =>
      HttpResponse.json({ title: "Internal Server Error" }, { status: 500 }),
    ),
    http.get("*/api/game-modules", () => HttpResponse.json([generic])),
  )

  renderApp("/")

  expect(await screen.findByText("Couldn't load the games")).toBeInTheDocument()
  expect(await screen.findByText("generic")).toBeInTheDocument()
})

test("shows a not-found page for an unknown address", async () => {
  renderApp("/no-such-page")

  expect(await screen.findByRole("heading", { name: "Page not found" })).toBeInTheDocument()
})
