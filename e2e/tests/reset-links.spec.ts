import { expectAccessible } from "../support/accessibility.ts"
import { MEMBER, SIGNED_OUT } from "../support/cast.ts"
import { expect, test } from "../support/fixtures.ts"

const NEW_PASSWORD = "a new password nobody guesses"

// Journey 3. The test starts as the Admin.
test("a Member who forgot their password chooses a new one through a Reset Link", async ({
  page,
  pageAs,
}) => {
  // Where the Member is still signed in from before, such as on another device.
  const elsewhere = await pageAs(MEMBER.state)

  const link = await test.step("the Admin creates a Reset Link for the Member", async () => {
    await page.goto("/players")
    await page
      .getByTestId("player-row")
      .filter({ hasText: MEMBER.player })
      .getByTestId("player-actions")
      .click()
    await page.getByTestId("create-reset-link").click()
    await page.getByTestId("create-link").click()
    return await page.getByTestId("link").inputValue()
  })

  const own = await pageAs(SIGNED_OUT)

  await test.step("the Member chooses a new password through the link", async () => {
    await own.goto(link)
    await expectAccessible(own)
    await own.getByTestId("password").fill(NEW_PASSWORD)
    await own.getByTestId("save-password").click()
    await own.getByTestId("account-menu").click()
    await expect(own.getByTestId("signed-in-player")).toHaveText(MEMBER.player)
  })

  await test.step("the new password signs in", async () => {
    await own.getByTestId("sign-out").click()
    await own.getByTestId("username").fill(MEMBER.username)
    await own.getByTestId("password").fill(NEW_PASSWORD)
    await own.getByTestId("sign-in").click()
    await expect(own.getByTestId("nav-plays")).toBeVisible()
  })

  await test.step("the sign-in from before has ended", async () => {
    await elsewhere.goto("/")
    await expect(elsewhere).toHaveURL(/\/sign-in$/)
  })
})
