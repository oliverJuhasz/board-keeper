import { expectAccessible } from "../support/accessibility.ts"
import { ADMIN, GENERIC_GAME } from "../support/cast.ts"
import { expect, test } from "../support/fixtures.ts"
import { addPlayers, playerRow, startRecording } from "../support/playForm.ts"

// Journey 4. The test starts as the Admin, whose Player is Dora.
test("a competitive Play of a Generic Game is recorded, and shows in the history and the statistics", async ({
  page,
}) => {
  await test.step("recording it", async () => {
    await startRecording(page, GENERIC_GAME)
    // Whoever records a Play is its first Player.
    await expect(playerRow(page, ADMIN.player)).toBeVisible()
    await addPlayers(page, ["Anna", "Bence"])
    await playerRow(page, ADMIN.player).getByTestId("score").fill("41")
    await playerRow(page, "Anna").getByTestId("score").fill("52")
    await playerRow(page, "Bence").getByTestId("score").fill("38")
    await expectAccessible(page)
    await page.getByTestId("save-play").click()
  })

  await test.step("the Play's page shows its Results, best first", async () => {
    await expect(page).toHaveURL(/\/plays\/[\w-]+$/)
    const results = page.getByTestId("result-row")
    await expect(results).toHaveCount(3)
    await expect(results.nth(0)).toContainText("Anna")
    await expect(results.nth(0).getByTestId("placing")).toHaveText("1")
    await expect(results.nth(0).getByTestId("winner")).toBeVisible()
    await expect(results.nth(0).getByTestId("total")).toHaveText("52")
    await expect(results.nth(1)).toContainText(ADMIN.player)
    await expect(results.nth(2)).toContainText("Bence")
    await expect(results.nth(2).getByTestId("placing")).toHaveText("3")
    await expectAccessible(page)
  })

  await test.step("the play history lists it", async () => {
    await page.getByTestId("nav-plays").click()
    const plays = page.getByTestId("play-summary")
    await expect(plays).toHaveCount(1)
    await expect(plays.first()).toContainText(GENERIC_GAME)
    await expectAccessible(page)
  })

  await test.step("the Game's statistics count it", async () => {
    await page.getByTestId("nav-games").click()
    const game = page.getByTestId("game-row").filter({ hasText: GENERIC_GAME })
    await game.getByTestId("game-actions").click()
    await page.getByTestId("game-statistics").click()
    await expect(page).toHaveURL(/\/games\/[\w-]+$/)

    // The statistics are worked out a moment after the Play is recorded (ADR 0011), so the page
    // may need loading again. toPass retries the whole block until it holds.
    const anna = page.getByTestId("stats-row").filter({ hasText: "Anna" })
    await expect(async () => {
      await page.reload()
      await expect(anna.getByTestId("wins")).toContainText("1 of 1", { timeout: 1_000 })
    }).toPass()
    await expect(anna.getByTestId("best-score")).toHaveText("52")
    await expect(
      page.getByTestId("stats-row").filter({ hasText: "Bence" }).getByTestId("wins"),
    ).toContainText("0 of 1")
    await expectAccessible(page)
  })
})
