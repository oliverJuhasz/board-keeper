import { expectAccessible } from "../support/accessibility.ts"
import { ADMIN, GENERIC_GAME } from "../support/cast.ts"
import { expect, test } from "../support/fixtures.ts"
import { addPlayers, playerRow, startRecording } from "../support/playForm.ts"

// Journey 10. The test starts as the Admin, whose Player is Dora.
test("a Play recorded without a connection waits on the device, and is sent once it is back", async ({
  page,
  context,
}) => {
  await startRecording(page, GENERIC_GAME)
  await addPlayers(page, ["Anna"])
  await playerRow(page, ADMIN.player).getByTestId("score").fill("61")
  await playerRow(page, "Anna").getByTestId("score").fill("57")

  await test.step("without a connection, the Play waits on the device", async () => {
    // Takes the whole browser off the network, as when a phone loses its signal.
    await context.setOffline(true)
    await expect(page.getByTestId("offline")).toBeVisible()
    await page.getByTestId("save-play").click()

    await expect(page).toHaveURL(/\/$/)
    await expect(page.getByTestId("waiting-play")).toContainText(GENERIC_GAME)
    await expectAccessible(page)
  })

  await test.step("once the connection is back, it is sent", async () => {
    await context.setOffline(false)
    await expect(page.getByTestId("waiting-play")).toHaveCount(0)
    await page.getByTestId("nav-plays").click()
    await expect(page.getByTestId("play-summary")).toHaveCount(1)
    await expect(page.getByTestId("play-summary")).toContainText(GENERIC_GAME)
  })
})
