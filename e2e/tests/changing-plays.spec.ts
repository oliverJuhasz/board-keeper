import type { Page } from "@playwright/test"
import { expectAccessible } from "../support/accessibility.ts"
import { ADMIN, GENERIC_GAME, MEMBER } from "../support/cast.ts"
import { expect, test } from "../support/fixtures.ts"
import { addPlayers, playerRow, startRecording } from "../support/playForm.ts"

// Journey 8. The tests start as the Admin, whose Player is Dora.

test("whoever recorded a Play edits it, and deletes it", async ({ page }) => {
  await recordPlay(page, { Anna: 30, [ADMIN.player]: 25 })

  await test.step("editing it scores it again", async () => {
    await page.getByTestId("play-actions").click()
    await page.getByTestId("edit-play").click()
    await expect(page).toHaveURL(/\/edit$/)
    await playerRow(page, ADMIN.player).getByTestId("score").fill("35")
    await page.getByTestId("notes").fill("Dora caught up in the last round.")
    await page.getByTestId("save-play").click()

    await expect(page).toHaveURL(/\/plays\/[\w-]+$/)
    const first = page.getByTestId("result-row").first()
    await expect(first).toContainText(ADMIN.player)
    await expect(first.getByTestId("total")).toHaveText("35")
    await expect(page.getByTestId("notes")).toHaveText("Dora caught up in the last round.")
  })

  await test.step("deleting it, after an are-you-sure, takes it out of the history", async () => {
    await page.getByTestId("play-actions").click()
    await page.getByTestId("delete-play").click()
    await expectAccessible(page)
    await page.getByTestId("confirm").click()
    await expect(page).toHaveURL(/\/plays$/)
    await expect(page.getByTestId("play-summary")).toHaveCount(0)
  })
})

test("a Member may change only the Plays they took part in", async ({ page, pageAs }) => {
  const withoutErik = await recordPlay(page, { Anna: 30, Bence: 25 })
  const withErik = await recordPlay(page, { Anna: 30, [MEMBER.player]: 25 })
  const erik = await pageAs(MEMBER.state)

  await test.step("a Play without Erik: he can record another, and change nothing", async () => {
    await erik.goto(withoutErik)
    await expect(erik.getByTestId("result-row")).toHaveCount(2)
    await expect(erik.getByTestId("record-another")).toBeVisible()
    await expect(erik.getByTestId("play-actions")).toBeHidden()
  })

  await test.step("a Play with Erik: he may change it", async () => {
    await erik.goto(withErik)
    await expect(erik.getByTestId("result-row")).toHaveCount(2)
    await expect(erik.getByTestId("play-actions")).toBeVisible()
  })
})

/**
 * Records a Play of the Generic Game with these Players and scores, through the form, and
 * answers the address of its page. The recorder, Dora, takes part only if she is named.
 */
async function recordPlay(page: Page, scores: Record<string, number>) {
  await startRecording(page, GENERIC_GAME)
  const names = Object.keys(scores)
  if (!names.includes(ADMIN.player)) {
    await playerRow(page, ADMIN.player).getByTestId("remove-player").click()
  }
  await addPlayers(
    page,
    names.filter((name) => name !== ADMIN.player),
  )
  for (const [name, score] of Object.entries(scores)) {
    await playerRow(page, name).getByTestId("score").fill(String(score))
  }
  await page.getByTestId("save-play").click()
  await expect(page).toHaveURL(/\/plays\/[\w-]+$/)
  return page.url()
}
