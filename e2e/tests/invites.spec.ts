import { expectAccessible } from "../support/accessibility.ts"
import { PASSWORD, SIGNED_OUT } from "../support/cast.ts"
import { expect, test } from "../support/fixtures.ts"

// Journey 2. The test starts as the Admin.
test("a Player registers an Account through an Invite and claims their Player", async ({
  page,
  pageAs,
}) => {
  const bence = page.getByTestId("player-row").filter({ hasText: "Bence" })

  const link = await test.step("the Admin invites Bence", async () => {
    await page.goto("/players")
    await expectAccessible(page)
    await bence.getByTestId("player-actions").click()
    await page.getByTestId("invite").click()
    await page.getByTestId("create-link").click()
    return await page.getByTestId("link").inputValue()
  })

  await test.step("Bence registers through the link, on a device of their own", async () => {
    const own = await pageAs(SIGNED_OUT)
    await own.goto(link)
    await expectAccessible(own)
    await own.getByTestId("username").fill("bence")
    await own.getByTestId("password").fill(PASSWORD)
    await own.getByTestId("register").click()

    await own.getByTestId("account-menu").click()
    await expect(own.getByTestId("signed-in-player")).toHaveText("Bence")
  })

  await test.step("Bence has an Account now", async () => {
    await page.getByTestId("link-done").click()
    await page.reload()
    await expect(bence.getByTestId("has-account")).toBeVisible()
  })
})
