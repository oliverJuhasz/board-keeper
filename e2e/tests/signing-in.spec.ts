import { expectAccessible } from "../support/accessibility.ts"
import { MEMBER, PASSWORD, SIGNED_OUT } from "../support/cast.ts"
import { expect, test } from "../support/fixtures.ts"

// Journey 1. The test starts signed out.
test.use({ storageState: SIGNED_OUT })

test("a Member signs in with their password and out again", async ({ page }) => {
  await page.goto("/")
  await expect(page).toHaveURL(/\/sign-in$/)
  await expectAccessible(page)

  await test.step("a wrong password is refused", async () => {
    await page.getByTestId("username").fill(MEMBER.username)
    await page.getByTestId("password").fill("not the password at all")
    await page.getByTestId("sign-in").click()
    await expect(page.getByTestId("problem")).toBeVisible()
  })

  await test.step("the right one signs in", async () => {
    await page.getByTestId("password").fill(PASSWORD)
    await page.getByTestId("sign-in").click()
    await page.getByTestId("account-menu").click()
    await expect(page.getByTestId("signed-in-player")).toHaveText(MEMBER.player)
  })

  await test.step("signing out ends the session on the server too", async () => {
    await page.getByTestId("sign-out").click()
    await expect(page).toHaveURL(/\/sign-in$/)
    await page.reload()
    await expect(page.getByTestId("sign-in")).toBeVisible()
  })
})
