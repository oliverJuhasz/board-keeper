import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { beforeEach, expect, test } from "vitest"
import type { Export, ImportResult, ImportReview } from "@/api/client"
import { anna, annaPlayer, bence, doraPlayer, problem, signedInAs } from "@/test/api"
import { optionOf } from "@/test/playForm"
import { inside, page } from "@/test/queries"
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
  const input = await page.findByTestId("import-file")
  expect(input).toHaveAccessibleName(label)
  await userEvent.upload(input, new File([contents], "board-keeper-2026-03-01.json"))
}

/** The row of one of the file's Players in the review, found by their name in the file. */
function fileRow(name: string) {
  const row = page
    .getAllByTestId("import-player")
    .find(
      (candidate) =>
        inside(candidate).getByTestId("import-player-choice-label").textContent === name,
    )
  if (!row) {
    throw new Error(`The review has no row for ${name}.`)
  }
  return inside(row)
}

/** Who one of the file's Players is here, as the review suggests it. */
function choiceOf(name: string) {
  return fileRow(name).getByTestId("import-player-choice")
}

test("reviews an export before anything is written: what it adds, and what it can't take", async () => {
  await chooseFile(JSON.stringify(file))

  const summary = await page.findByTestId("import-summary")
  expect(summary).toHaveTextContent("3 plays to add")
  expect(summary).toHaveTextContent("1 play is here already and stays as it is")
  expect(summary).toHaveTextContent("2 new players")
  expect(summary).toHaveTextContent("1 new game: Catan")
  expect(page.getByTestId("refused-plays")).toHaveTextContent("2 plays can't be imported:")
  expect(page.getAllByTestId("refused-play").map((play) => play.textContent)).toEqual([
    expect.stringMatching(/^2 February 2026, Calico: .+/),
    "3 February 2026, Fancy Game: This Board Keeper can't score this game (fancy-game). Update Board Keeper first.",
  ])
  expect(imported).toHaveLength(0)
})

test("suggests the player of the same name, and a new player for the others", async () => {
  await chooseFile(JSON.stringify(file))

  await page.findByTestId("who-is-who")
  expect(choiceOf("Dora")).toHaveValue(doraPlayer.id)
  expect(choiceOf("Anna Kovács")).toHaveValue("new")
  const annasName = fileRow("Anna Kovács").getByTestId("import-player-name")
  expect(annasName).toHaveAccessibleName("Anna Kovács's name here")
  expect(annasName).toHaveValue("Anna Kovács")
  expect(fileRow("Zsófi").getByTestId("import-player-name")).toHaveValue("Zsófi")
  // Dora is taken by the file's Dora, so nobody else is offered her.
  expect(optionOf(choiceOf("Anna Kovács"), doraPlayer.id)).toBeUndefined()
  expect(optionOf(choiceOf("Anna Kovács"), annaPlayer.id)).toHaveTextContent("Anna")
})

test("imports with who is who, and says what it did", async () => {
  await chooseFile(JSON.stringify(file))
  await page.findByTestId("who-is-who")
  await userEvent.selectOptions(choiceOf("Anna Kovács"), annaPlayer.id)
  await userEvent.click(page.getByTestId("confirm-import"))

  const done = await page.findByTestId("import-done")
  expect(done).toHaveTextContent("Imported 3 plays.")
  expect(done).toHaveTextContent("Added 1 new player")
  expect(done).toHaveTextContent("Added 1 new game")
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

  await userEvent.click(page.getByTestId("import-another"))
  expect(await page.findByTestId("import-file")).toBeVisible()
})

test("a new player needs a name nobody here has yet", async () => {
  await chooseFile(JSON.stringify(file))
  await page.findByTestId("who-is-who")
  const name = fileRow("Zsófi").getByTestId("import-player-name")
  await userEvent.clear(name)
  await userEvent.type(name, "anna")
  await userEvent.click(page.getByTestId("confirm-import"))

  expect(
    await page.findByTestIdWith("field-error", "There is already a player with this name."),
  ).toBeVisible()
  expect(imported).toHaveLength(0)
})

test("cancelling goes back to choosing a file", async () => {
  await chooseFile(JSON.stringify(file))
  await userEvent.click(await page.findByTestId("cancel-import"))

  expect(await page.findByTestId("import-file")).toBeVisible()
  expect(page.queryByTestId("who-is-who")).not.toBeInTheDocument()
})

test("says so when a file isn't an export", async () => {
  await chooseFile("Not JSON at all")

  expect(await page.findByTestId("problem")).toHaveTextContent(
    "This file isn't an export of Board Keeper.",
  )
})

test("says so when the export comes from a newer Board Keeper", async () => {
  server.use(http.post("*/api/import/review", () => problem(422, "imports.formatTooNew")))
  await chooseFile(JSON.stringify({ ...file, format: 2 }))

  expect(await page.findByTestId("problem")).toHaveTextContent(
    "This export was made by a newer Board Keeper. Update this one first.",
  )
})

test("an Admin without the right to import sees only the export", async () => {
  signedInAs({ ...anna, permissions: anna.permissions.filter((p) => p !== "IMPORT") })
  renderApp("/export-and-import")

  expect(await page.findByTestId("download-export")).toBeVisible()
  expect(page.queryByTestId("import-file")).not.toBeInTheDocument()
})

test("is not there for anyone else", async () => {
  signedInAs(bence)
  renderApp("/export-and-import")

  expect(await page.findByTestIdWith("page-title", "Page not found")).toBeInTheDocument()
  expect(page.queryByTestId("import-file")).not.toBeInTheDocument()
})

test("is in Hungarian", async () => {
  signedInAs({ ...anna, language: "hu" })
  await chooseFile(JSON.stringify(file), "Exportfájl")

  expect(await page.findByTestId("import-summary")).toHaveTextContent("3 hozzáadandó parti")
  expect(page.getByTestId("who-is-who")).toHaveTextContent("Ki kicsoda")
  expect(page.getByTestId("confirm-import")).toHaveTextContent("Importálás")
})
