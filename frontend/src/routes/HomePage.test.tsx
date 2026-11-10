import { screen } from "@testing-library/react"
import { http } from "msw"
import { beforeEach, expect, test } from "vitest"
import { anna, gamesAre, genericModule, problem, signedInAs } from "@/test/api"
import { renderApp } from "@/test/renderApp"
import { server } from "@/test/server"

beforeEach(() => signedInAs(anna))

test("shows the installed game modules by name, and that there are no games yet", async () => {
  gamesAre([])

  renderApp("/")

  expect(await screen.findByText("Generic")).toBeInTheDocument()
  expect(await screen.findByText("No games yet.")).toBeInTheDocument()
})

test("lists the games in the order the API returns them", async () => {
  gamesAre([
    { id: "0193a000-0000-7000-8000-000000000001", name: "Calico", gameModuleId: genericModule.id },
    {
      id: "0193a000-0000-7000-8000-000000000002",
      name: "Wyrmspan",
      gameModuleId: genericModule.id,
    },
  ])

  renderApp("/")

  await screen.findByText("Calico")
  await screen.findByText("Generic")
  expect(screen.getAllByRole("listitem").map((item) => item.textContent)).toEqual([
    "Calico",
    "Wyrmspan",
    "Generic",
  ])
})

test("says so when the games can't be loaded", async () => {
  gamesAre([])
  server.use(http.get("*/api/games", () => problem(500, "unexpected")))

  renderApp("/")

  expect(await screen.findByText("Couldn't load the games")).toBeInTheDocument()
  expect(await screen.findByText("Generic")).toBeInTheDocument()
})

test("shows a game module's key while its texts can't be loaded", async () => {
  gamesAre([])
  server.use(http.get("*/api/game-modules/texts/:language", () => problem(500, "unexpected")))

  renderApp("/")

  expect(await screen.findByText("generic")).toBeInTheDocument()
})

test("uses the language saved on the account", async () => {
  signedInAs({ ...anna, language: "nl" })
  gamesAre([])

  renderApp("/")

  expect(await screen.findByText("Nog geen spellen.")).toBeInTheDocument()
})

test("shows a not-found page for an unknown address", async () => {
  renderApp("/no-such-page")

  expect(await screen.findByRole("heading", { name: "Page not found" })).toBeInTheDocument()
})
