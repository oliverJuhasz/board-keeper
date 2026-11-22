import type { Page } from "@playwright/test"
import { expectAccessible } from "../support/accessibility.ts"
import { MEMBER } from "../support/cast.ts"
import { expect, test } from "../support/fixtures.ts"

// Journey 9. The tests start as the Member. The sign-in page offers a Passkey in two ways: the
// "Sign in with a passkey" button, and, where the browser can, among its suggestions in the
// username field. Each test follows one.
test.use({ storageState: MEMBER.state })

test("a Member adds a Passkey and signs in with it from the button", async ({ page }) => {
  // The browser is told it can't suggest Passkeys, so that only the button offers one.
  // Otherwise the pretend phone answers the suggestions at once, before the button is pressed.
  await page.addInitScript(() => {
    PublicKeyCredential.isConditionalMediationAvailable = async () => false
  })
  await addPasskey(page)

  await signOut(page)
  await page.getByTestId("sign-in-with-passkey").click()
  await expectSignedIn(page)
})

test("a Member adds a Passkey and signs in with it from the browser's suggestions", async ({
  page,
}) => {
  await addPasskey(page)

  // The sign-in page asks the browser for its suggestions as it opens, and the pretend phone
  // picks this site's Passkey from them.
  await signOut(page)
  await expectSignedIn(page)
})

/**
 * Adds a Passkey in Settings, on a pretend phone built into Chromium: it holds passkeys and
 * always says yes to the fingerprint. It is set up through Chromium's DevTools protocol, the
 * interface its developer tools use, so only Chromium has it.
 */
async function addPasskey(page: Page) {
  const chromium = await page.context().newCDPSession(page)
  await chromium.send("WebAuthn.enable")
  await chromium.send("WebAuthn.addVirtualAuthenticator", {
    options: {
      protocol: "ctap2",
      transport: "internal",
      hasResidentKey: true,
      hasUserVerification: true,
      isUserVerified: true,
      automaticPresenceSimulation: true,
    },
  })

  await page.goto("/")
  await page.getByTestId("account-menu").click()
  await page.getByTestId("settings").click()
  await page.getByTestId("add-passkey").click()
  await page.getByTestId("passkey-label").fill("Pretend phone")
  await page.getByTestId("save-passkey").click()
  await expect(page.getByTestId("passkey")).toContainText("Pretend phone")
  await expectAccessible(page)
}

async function signOut(page: Page) {
  await page.getByTestId("account-menu").click()
  await page.getByTestId("sign-out").click()
}

async function expectSignedIn(page: Page) {
  await page.getByTestId("account-menu").click()
  await expect(page.getByTestId("signed-in-player")).toHaveText(MEMBER.player)
}
