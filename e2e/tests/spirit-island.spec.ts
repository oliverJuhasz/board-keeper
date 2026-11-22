import { expectAccessible } from "../support/accessibility.ts"
import { ADMIN } from "../support/cast.ts"
import { expect, test } from "../support/fixtures.ts"
import { addPlayers, playerRow, startRecording } from "../support/playForm.ts"

// Journey 6. The test starts as the Admin, whose Player is Dora.
test("a Cooperative Play of Spirit Island, whose group score the Game Module works out", async ({
  page,
}) => {
  await startRecording(page, "Spirit Island")
  await addPlayers(page, ["Anna"])
  await playerRow(page, ADMIN.player)
    .getByTestId("player-setup-spirit")
    .selectOption("riverSurgesInSunlight")
  await playerRow(page, "Anna")
    .getByTestId("player-setup-spirit")
    .selectOption("lightningsSwiftStrike")

  await test.step("scoring is optional, so the Play starts with only won or lost", async () => {
    await page.getByTestId("change-recording").click()
    await page.getByTestId("recording-mode-POINTS").check()
    await page.getByTestId("group-outcome-won").check()
  })

  await test.step("the score follows the rulebook", async () => {
    // Without an adversary or a scenario the Difficulty is 0, and with two Players a Dahan or a
    // Blight counts per complete group of two: 5 × 0 + 10 + 2 × 4 + 10 / 2 − floor(3 / 2) = 22.
    await page.getByTestId("group-score-invaderCards").fill("4")
    await page.getByTestId("group-score-dahan").fill("10")
    await page.getByTestId("group-score-blight").fill("3")
    await expect(page.getByTestId("group-total")).toContainText("22")
    await expectAccessible(page)
    await page.getByTestId("save-play").click()
  })

  await test.step("the Play's page shows the group's victory and score", async () => {
    await expect(page.getByTestId("group-outcome")).toHaveText("Won together")
    await expect(page.getByTestId("group-total")).toContainText("22")
    await expect(page.getByTestId("result-row")).toHaveText([
      `${ADMIN.player} · River Surges in Sunlight`,
      "Anna · Lightning's Swift Strike",
    ])
    await expectAccessible(page)
  })
})
