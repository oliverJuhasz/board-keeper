import { screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { expect, test } from "vitest"
import type { Game, GameModule } from "@/api/client"
import { renderApp } from "@/test/renderApp"
import { server } from "@/test/server"

const generic: GameModule = { id: "38b119d2-8f9f-4e20-8068-cba305c06cdd", key: "generic" }

const gameModuleTexts: Record<string, Record<string, string>> = {
  en: { "generic.name": "Generic" },
  nl: { "generic.name": "Algemeen" },
  hu: { "generic.name": "Általános" },
}

function apiReturns({ games, gameModules }: { games: Game[]; gameModules: GameModule[] }) {
  server.use(
    http.get("*/api/games", () => HttpResponse.json(games)),
    http.get("*/api/game-modules", () => HttpResponse.json(gameModules)),
    http.get("*/api/game-modules/texts/:language", ({ params }) =>
      HttpResponse.json(gameModuleTexts[params.language as string]),
    ),
  )
}

test("shows the installed game modules by name, and that there are no games yet", async () => {
  apiReturns({ games: [], gameModules: [generic] })

  renderApp("/")

  expect(await screen.findByText("Generic")).toBeInTheDocument()
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
  await screen.findByText("Generic")
  expect(screen.getAllByRole("listitem").map((item) => item.textContent)).toEqual([
    "Calico",
    "Wyrmspan",
    "Generic",
  ])
})

test("says so when the games can't be loaded", async () => {
  apiReturns({ games: [], gameModules: [generic] })
  server.use(
    http.get("*/api/games", () =>
      HttpResponse.json({ title: "Internal Server Error" }, { status: 500 }),
    ),
  )

  renderApp("/")

  expect(await screen.findByText("Couldn't load the games")).toBeInTheDocument()
  expect(await screen.findByText("Generic")).toBeInTheDocument()
})

test("shows a game module's key while its texts can't be loaded", async () => {
  apiReturns({ games: [], gameModules: [generic] })
  server.use(
    http.get("*/api/game-modules/texts/:language", () => HttpResponse.json({}, { status: 500 })),
  )

  renderApp("/")

  expect(await screen.findByText("generic")).toBeInTheDocument()
})

test("switches everything to the chosen language and remembers it", async () => {
  apiReturns({ games: [], gameModules: [generic] })
  renderApp("/")
  await screen.findByText("Generic")

  await userEvent.selectOptions(screen.getByRole("combobox", { name: "Language" }), "Magyar")

  // The frontend's own texts, and the Game Module's name from the API.
  expect(await screen.findByText("Még nincsenek játékok.")).toBeInTheDocument()
  expect(await screen.findByText("Általános")).toBeInTheDocument()
  expect(screen.getByRole("combobox", { name: "Nyelv" })).toHaveValue("hu")
  expect(document.documentElement).toHaveAttribute("lang", "hu")
  expect(localStorage.getItem("boardkeeper.language")).toBe("hu")
})

test("shows a not-found page for an unknown address", async () => {
  renderApp("/no-such-page")

  expect(await screen.findByRole("heading", { name: "Page not found" })).toBeInTheDocument()
})
