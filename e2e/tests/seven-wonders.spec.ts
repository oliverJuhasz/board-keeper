import { expectAccessible } from "../support/accessibility.ts"
import { ADMIN } from "../support/cast.ts"
import { expect, test } from "../support/fixtures.ts"
import {
  addPlayers,
  fillScorePad,
  playerRow,
  scorePadPlayers,
  startRecording,
} from "../support/playForm.ts"

// Journey 5. The test starts as the Admin, whose Player is Dora.
test("a Play of 7 Wonders with Leaders, a Wonder board of its own, and a tie the recorder decides", async ({
  page,
}) => {
  await startRecording(page, "7 Wonders")

  await test.step("Roma can be chosen once Leaders is ticked", async () => {
    await addPlayers(page, ["Anna", "Bence"])
    const annasWonder = playerRow(page, "Anna").getByTestId("player-setup-wonder")
    // The Wonder boards of Leaders aren't offered without it.
    await expect(annasWonder).not.toContainText("Roma")
    await page.getByTestId("expansion-leaders").check()
    await annasWonder.selectOption("roma")
  })

  await test.step("the score pad has a row for the Leaders, and adds up", async () => {
    await expect(scorePadPlayers(page)).toHaveText([ADMIN.player, "Anna", "Bence"])
    // The same total for Dora and Anna: 45.
    await fillScorePad(page, {
      [ADMIN.player]: {
        wonder: 5,
        treasury: 4,
        military: 6,
        civilian: 10,
        commercial: 3,
        scientific: 8,
        guilds: 4,
        leaders: 5,
      },
      Anna: {
        wonder: 7,
        treasury: 3,
        military: 5,
        civilian: 9,
        commercial: 4,
        scientific: 10,
        guilds: 2,
        leaders: 5,
      },
      Bence: {
        wonder: 3,
        treasury: 2,
        military: -3,
        civilian: 6,
        commercial: 2,
        scientific: 5,
        guilds: 0,
        leaders: 4,
      },
    })
    await expect(page.getByTestId("total")).toHaveText(["45", "45", "19"])
  })

  await test.step("the coins decide the tie, and they aren't on the pad: the recorder is asked", async () => {
    await page.getByTestId("tie-winner-Anna").check()
    await expect(page.getByTestId("placing")).toHaveText(["2", "1", "3"])
    await expectAccessible(page)
    await page.getByTestId("save-play").click()
  })

  await test.step("the Play's page shows Anna first, with Roma", async () => {
    const results = page.getByTestId("result-row")
    await expect(results).toHaveCount(3)
    await expect(results.nth(0)).toContainText("Anna")
    await expect(results.nth(0).getByTestId("placing")).toHaveText("1")
    await expect(results.nth(0).getByTestId("winner")).toBeVisible()
    await expect(results.nth(0).getByTestId("player-setup")).toHaveText("Roma")
    await expect(results.nth(1)).toContainText(ADMIN.player)
    await expect(results.nth(1).getByTestId("placing")).toHaveText("2")
    await expect(results.nth(1).getByTestId("total")).toHaveText("45")
    await expectAccessible(page)
  })
})
