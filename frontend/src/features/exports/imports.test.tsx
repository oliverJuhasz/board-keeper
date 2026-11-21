import { screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { beforeEach, expect, test } from "vitest"
import type { Export, ImportResult, ImportReview } from "@/api/client"
import { anna, annaPlayer, bence, doraPlayer, problem, signedInAs } from "@/test/api"
import { renderApp } from "@/test/renderApp"
import { server } from "@/test/server"

/** An Export from another Installation, as far as the page looks at it: the page passes it on. */
const file = {
  format: 1,
  exportedAt: "2026-03-01T18:00:00Z",
  writtenBy: {},
  gameModules: [],
  games: [],
  players: [],
  plays: [],
} satisfies Export

const FILE_ANNA = "0190a000-0000-7000-8000-00000000b001"
const FILE_DORA = "0190a000-0000-7000-8000-00000000b002"
const FILE_ZSOFI = "0190a000-0000-7000-8000-00000000b003"

/** Its Dora is Dora here; Anna and Zsófi have nobody of their name here. */
const review: ImportReview = {
  games: [
    {
      fileId: "g1",
      name: "Calico",
      match: "CATALOGUE",
      gameId: "0193a000-0000-7000-8000-0000000000e4",
    },
    { fileId: "g2", name: "Catan", match: "NEW" },
    { fileId: "g3", name: "Fancy Game", match: "MODULE_MISSING", gameModuleKey: "fancy-game" },
  ],
  players: [
    { fileId: FILE_ANNA, name: "Anna Kovács" },
    { fileId: FILE_DORA, name: "Dora", suggestedPlayerId: doraPlayer.id },
    { fileId: FILE_ZSOFI, name: "Zsófi" },
  ],
  plays: 6,
  alreadyHere: 1,
  refused: [
    {
      playId: "c3",
      playedOn: "2026-02-02",
      game: "Calico",
      reason: "plays.invalid.scoreNotAllowed",
    },
    {
      playId: "c4",
      playedOn: "2026-02-03",
      game: "Fancy Game",
      reason: "imports.gameModuleMissing",
      gameModuleKey: "fancy-game",
    },
  ],
}

const result: ImportResult = {
  added: 3,
  alreadyHere: 1,
  refused: review.refused,
  newPlayers: 1,
  newGames: 1,
}

/** What the page sent to be imported. */
let imported: unknown[]

beforeEach(() => {
  imported = []
  signedInAs(anna)
  server.use(
    http.get("*/api/players", () => HttpResponse.json([annaPlayer, doraPlayer])),
    http.post("*/api/import/review", () => HttpResponse.json(review)),
    http.post("*/api/import", async ({ request }) => {
      imported.push(await request.json())
      return HttpResponse.json(result)
    }),
  )
})

async function chooseFile(contents: string, label = "Export file") {
  renderApp("/export-and-import")
  const input = await screen.findByLabelText(label)
  await userEvent.upload(input, new File([contents], "board-keeper-2026-03-01.json"))
}

test("reviews an export before anything is written: what it adds, and what it can't take", async () => {
  await chooseFile(JSON.stringify(file))

  expect(await screen.findByText("3 plays to add")).toBeVisible()
  expect(screen.getByText("1 play is here already and stays as it is")).toBeVisible()
  expect(screen.getByText("2 new players")).toBeVisible()
  expect(screen.getByText("1 new game: Catan")).toBeVisible()
  expect(screen.getByText("2 plays can't be imported:")).toBeVisible()
  expect(screen.getByText(/2 February 2026, Calico: .+/, { selector: "li" })).toBeInTheDocument()
  expect(
    screen.getByText(
      "3 February 2026, Fancy Game: This Board Keeper can't score this game (fancy-game). Update Board Keeper first.",
    ),
  ).toBeVisible()
  expect(imported).toHaveLength(0)
})

test("suggests the player of the same name, and a new player for the others", async () => {
  await chooseFile(JSON.stringify(file))

  expect(await screen.findByRole("combobox", { name: "Dora" })).toHaveValue(doraPlayer.id)
  expect(screen.getByRole("combobox", { name: "Anna Kovács" })).toHaveValue("new")
  expect(screen.getByRole("textbox", { name: "Anna Kovács's name here" })).toHaveValue(
    "Anna Kovács",
  )
  expect(screen.getByRole("textbox", { name: "Zsófi's name here" })).toHaveValue("Zsófi")
  // Dora is taken by the file's Dora, so nobody else is offered her.
  const anna = screen.getByRole("combobox", { name: "Anna Kovács" })
  expect(within(anna).queryByRole("option", { name: "Dora" })).not.toBeInTheDocument()
  expect(within(anna).getByRole("option", { name: "Anna" })).toBeInTheDocument()
})

test("imports with who is who, and says what it did", async () => {
  await chooseFile(JSON.stringify(file))
  await userEvent.selectOptions(
    await screen.findByRole("combobox", { name: "Anna Kovács" }),
    annaPlayer.id,
  )
  await userEvent.click(screen.getByRole("button", { name: "Import" }))

  expect(await screen.findByText("Imported 3 plays.")).toBeVisible()
  expect(screen.getByText("Added 1 new player")).toBeVisible()
  expect(screen.getByText("Added 1 new game")).toBeVisible()
  expect(imported).toEqual([
    {
      file,
      players: [
        { fileId: FILE_ANNA, playerId: annaPlayer.id },
        { fileId: FILE_DORA, playerId: doraPlayer.id },
        { fileId: FILE_ZSOFI, newName: "Zsófi" },
      ],
    },
  ])

  await userEvent.click(screen.getByRole("button", { name: "Import another export" }))
  expect(await screen.findByLabelText("Export file")).toBeVisible()
})

test("a new player needs a name nobody here has yet", async () => {
  await chooseFile(JSON.stringify(file))
  const name = await screen.findByRole("textbox", { name: "Zsófi's name here" })
  await userEvent.clear(name)
  await userEvent.type(name, "anna")
  await userEvent.click(screen.getByRole("button", { name: "Import" }))

  expect(await screen.findByText("There is already a player with this name.")).toBeVisible()
  expect(imported).toHaveLength(0)
})

test("cancelling goes back to choosing a file", async () => {
  await chooseFile(JSON.stringify(file))
  await userEvent.click(await screen.findByRole("button", { name: "Cancel" }))

  expect(await screen.findByLabelText("Export file")).toBeVisible()
  expect(screen.queryByText("Who is who")).not.toBeInTheDocument()
})

test("says so when a file isn't an export", async () => {
  await chooseFile("Not JSON at all")

  expect(await screen.findByText("This file isn't an export of Board Keeper.")).toBeVisible()
})

test("says so when the export comes from a newer Board Keeper", async () => {
  server.use(http.post("*/api/import/review", () => problem(422, "imports.formatTooNew")))
  await chooseFile(JSON.stringify({ ...file, format: 2 }))

  expect(
    await screen.findByText("This export was made by a newer Board Keeper. Update this one first."),
  ).toBeVisible()
})

test("an Admin without the right to import sees only the export", async () => {
  signedInAs({ ...anna, permissions: anna.permissions.filter((p) => p !== "IMPORT") })
  renderApp("/export-and-import")

  expect(await screen.findByRole("button", { name: "Download an export" })).toBeVisible()
  expect(screen.queryByLabelText("Export file")).not.toBeInTheDocument()
})

test("is not there for anyone else", async () => {
  signedInAs(bence)
  renderApp("/export-and-import")

  expect(await screen.findByRole("heading", { name: "Page not found" })).toBeInTheDocument()
  expect(screen.queryByLabelText("Export file")).not.toBeInTheDocument()
})

test("is in Hungarian", async () => {
  signedInAs({ ...anna, language: "hu" })
  await chooseFile(JSON.stringify(file), "Exportfájl")

  expect(await screen.findByText("3 hozzáadandó parti")).toBeVisible()
  expect(screen.getByText("Ki kicsoda")).toBeVisible()
  expect(screen.getByRole("button", { name: "Importálás" })).toBeVisible()
})
