import { readFile } from "node:fs/promises"
import { fileURLToPath } from "node:url"
import type { Page } from "@playwright/test"
import { expectAccessible } from "../support/accessibility.ts"
import { ADMIN, GENERIC_GAME, PLAYERS } from "../support/cast.ts"
import { expect, test } from "../support/fixtures.ts"

/**
 * The sample Export of the first format, which the backend's tests import too (ImportApiTests).
 * Its Players are Anna, Dora and Zsófi; it has Plays of Calico, of Generic Games, and of a Game
 * whose Game Module this Installation doesn't have.
 */
const SAMPLE = fileURLToPath(
  new URL("../../backend/app/src/test/resources/exports/format-1.json", import.meta.url),
)

// Journey 11. The tests start as the Admin.

test("an Admin downloads an Export of the Games, Players and Plays", async ({ page }) => {
  await openExportAndImport(page)

  // Playwright catches the file the browser saves.
  const downloading = page.waitForEvent("download")
  await page.getByTestId("download-export").click()
  const download = await downloading

  expect(download.suggestedFilename()).toMatch(/^board-keeper-\d{4}-\d{2}-\d{2}\.json$/)
  const file = JSON.parse(await readFile(await download.path(), "utf8"))
  expect(file.format).toBe(1)
  const players: string[] = file.players.map((player: { name: string }) => player.name)
  expect(players).toEqual(expect.arrayContaining([ADMIN.player, ...PLAYERS]))
  const games: string[] = file.games.map((game: { name: string }) => game.name)
  expect(games).toContain(GENERIC_GAME)
})

test("an Admin imports an Export, after reviewing who is who", async ({ page }) => {
  await openExportAndImport(page)
  await page.getByTestId("import-file").setInputFiles(SAMPLE)

  await test.step("the review matches Players by name, and offers to add the others", async () => {
    await expect(page.getByTestId("import-summary")).toBeVisible()
    const player = (name: string) =>
      page
        .getByTestId("import-player")
        .filter({ hasText: name })
        .getByTestId("import-player-choice")
    // "new" is the choice to add someone as a new Player.
    await expect(player("Anna")).not.toHaveValue("new")
    await expect(player("Dora")).not.toHaveValue("new")
    await expect(player("Zsófi")).toHaveValue("new")
    await expectAccessible(page)
  })

  await test.step("confirming imports the Plays", async () => {
    await page.getByTestId("confirm-import").click()
    await expect(page.getByTestId("import-done")).toBeVisible()
    await page.getByTestId("nav-plays").click()
    await expect(page.getByTestId("play-summary").filter({ hasText: "Catan" })).not.toHaveCount(0)
  })

  await test.step("Zsófi is a Player here now", async () => {
    await page.getByTestId("nav-players").click()
    await expect(page.getByTestId("player-row").filter({ hasText: "Zsófi" })).toBeVisible()
  })
})

async function openExportAndImport(page: Page) {
  await page.goto("/")
  await page.getByTestId("account-menu").click()
  await page.getByTestId("export-and-import").click()
  await expect(page).toHaveURL(/\/export-and-import$/)
  await expectAccessible(page)
}
