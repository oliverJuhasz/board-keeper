import { screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { afterEach, beforeEach, expect, test, vi } from "vitest"
import { anna, bence, problem, signedInAs } from "@/test/api"
import { renderApp } from "@/test/renderApp"
import { server } from "@/test/server"

const exportFile = { format: 1, games: [], players: [], plays: [] }

/** What the browser was asked to save: the file and the name to save it under. */
let saved: { file: Blob; name: string }[]

beforeEach(() => {
  saved = []
  // The test's browser keeps no files, so making an address for one and clicking a link to it
  // are only watched.
  let file: Blob | undefined
  URL.createObjectURL = vi.fn((blob: Blob) => {
    file = blob
    return "blob:export"
  })
  URL.revokeObjectURL = vi.fn()
  vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (
    this: HTMLAnchorElement,
  ) {
    if (file) {
      saved.push({ file, name: this.download })
    }
  })
  server.use(
    http.get("*/api/export", () =>
      HttpResponse.json(exportFile, {
        headers: { "Content-Disposition": 'attachment; filename="board-keeper-2026-11-21.json"' },
      }),
    ),
  )
})

afterEach(() => {
  vi.restoreAllMocks()
})

test("an Admin finds export and import in the account menu", async () => {
  signedInAs(anna)
  renderApp()

  await userEvent.click(await screen.findByRole("button", { name: "Account" }))
  expect(await screen.findByRole("menuitem", { name: "Export and import" })).toHaveAttribute(
    "href",
    "/export-and-import",
  )
})

test("an Admin downloads an export as a file, under the name the server gives it", async () => {
  signedInAs(anna)
  renderApp("/export-and-import")

  expect(await screen.findByRole("heading", { name: "Export and import" })).toBeInTheDocument()
  expect(screen.getByText(/It holds nothing about accounts, so it is no backup/)).toBeVisible()
  await userEvent.click(screen.getByRole("button", { name: "Download an export" }))

  await vi.waitFor(() => expect(saved).toHaveLength(1))
  expect(saved[0].name).toBe("board-keeper-2026-11-21.json")
  expect(JSON.parse(await saved[0].file.text())).toEqual(exportFile)
})

test("says why when the export fails", async () => {
  signedInAs(anna)
  server.use(http.get("*/api/export", () => problem(403, "access.denied")))
  renderApp("/export-and-import")

  await userEvent.click(await screen.findByRole("button", { name: "Download an export" }))

  expect(await screen.findByText("You aren't allowed to do this.")).toBeVisible()
  expect(saved).toHaveLength(0)
})

test("anyone else finds neither the menu item nor the page", async () => {
  signedInAs(bence)
  renderApp("/export-and-import")

  expect(await screen.findByRole("heading", { name: "Page not found" })).toBeInTheDocument()
  expect(screen.queryByRole("button", { name: "Download an export" })).not.toBeInTheDocument()
  await userEvent.click(screen.getByRole("button", { name: "Account" }))
  expect(await screen.findByRole("menuitem", { name: "Settings" })).toBeInTheDocument()
  expect(screen.queryByRole("menuitem", { name: "Export and import" })).not.toBeInTheDocument()
})

test("is in Dutch", async () => {
  signedInAs({ ...anna, language: "nl" })
  renderApp("/export-and-import")

  expect(
    await screen.findByRole("heading", { name: "Exporteren en importeren" }),
  ).toBeInTheDocument()
  expect(screen.getByRole("button", { name: "Export downloaden" })).toBeVisible()
})

test("is in Hungarian", async () => {
  signedInAs({ ...anna, language: "hu" })
  renderApp("/export-and-import")

  expect(
    await screen.findByRole("heading", { name: "Exportálás és importálás" }),
  ).toBeInTheDocument()
  expect(screen.getByRole("button", { name: "Export letöltése" })).toBeVisible()
})
