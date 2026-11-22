import { ADMIN } from "../support/cast.ts"
import { expect, test } from "../support/fixtures.ts"

// Journey 12. The test starts as the Admin, with the browser in English.
test("switching to Hungarian lasts: after a reload, and on another device", async ({
  page,
  pageAs,
}) => {
  // The page's language is on its <html> element, which tells the browser and screen readers.
  const language = (on = page) => on.locator("html")

  await page.goto("/")
  await expect(language()).toHaveAttribute("lang", "en")
  await page.getByTestId("account-menu").click()
  await page.getByTestId("settings").click()

  // The choice is saved on the Account too, by a request of its own. The test waits for its
  // answer, as a person takes longer than that before reloading: a reload cancels a request that
  // is still on its way.
  const savedOnAccount = page.waitForResponse(
    (response) =>
      response.url().endsWith("/api/account/language") && response.request().method() === "PUT",
  )
  await page.getByTestId("language").selectOption("hu")
  await expect(language()).toHaveAttribute("lang", "hu")
  expect((await savedOnAccount).ok()).toBe(true)

  await test.step("after a reload", async () => {
    await page.reload()
    await expect(language()).toHaveAttribute("lang", "hu")
  })

  await test.step("on another device, signed in to the same Account", async () => {
    const other = await pageAs(ADMIN.state)
    await other.goto("/")
    await expect(language(other)).toHaveAttribute("lang", "hu")
  })
})
