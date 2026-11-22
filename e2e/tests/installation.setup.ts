import { mkdir, writeFile } from "node:fs/promises"
import { dirname } from "node:path"
import { expect, test as setup, type Page } from "@playwright/test"
import { ADMIN, GENERIC_GAME, MEMBER, PASSWORD, PLAYERS } from "../support/cast.ts"
import { keepCopy, setupToken } from "../support/installation.ts"

// The setup project: it runs before every other test (see "projects" in playwright.config.ts).
// It sets up a new Installation through the screens, as its first Admin would, and keeps a copy
// of the database for every test to start from. Its steps follow one another, so they run in
// this order.
setup.describe.configure({ mode: "serial" })

setup("the first Admin is created through the Setup Link", async ({ page }) => {
  // Journey 13. Board Keeper writes the link to its log while it has no Account.
  await page.goto(`/setup?token=${await setupToken()}`)
  await page.getByTestId("player-name").fill(ADMIN.player)
  await page.getByTestId("username").fill(ADMIN.username)
  await page.getByTestId("password").fill(PASSWORD)
  await page.getByTestId("set-up").click()

  // The new Admin is signed in, on the start page.
  await expect(page.getByTestId("add-game")).toBeVisible()
  await page.getByTestId("account-menu").click()
  await expect(page.getByTestId("signed-in-player")).toHaveText(ADMIN.player)

  await saveSignIn(page, ADMIN.state)
})

setup("the Admin adds Players, a Member and a Game", async ({ browser }) => {
  const admin = await (await browser.newContext({ storageState: ADMIN.state })).newPage()

  await setup.step("Players", async () => {
    await admin.goto("/players")
    for (const name of [...PLAYERS, MEMBER.player]) {
      await addPlayer(admin, name)
    }
  })

  await setup.step("a Member, through an Invite", async () => {
    await admin
      .getByTestId("player-row")
      .filter({ hasText: MEMBER.player })
      .getByTestId("player-actions")
      .click()
    await admin.getByTestId("invite").click()
    await admin.getByTestId("create-link").click()
    const link = await admin.getByTestId("link").inputValue()

    const member = await (await browser.newContext()).newPage()
    await member.goto(link)
    await member.getByTestId("username").fill(MEMBER.username)
    await member.getByTestId("password").fill(PASSWORD)
    await member.getByTestId("register").click()
    await expect(member.getByTestId("nav-plays")).toBeVisible()
    await saveSignIn(member, MEMBER.state)
    await member.context().close()
  })

  await setup.step("a Game without a Game Module of its own", async () => {
    await admin.goto("/")
    await admin.getByTestId("add-game").click()
    await admin.getByTestId("game-name").fill(GENERIC_GAME)
    await admin.getByTestId("save-game").click()
    await expect(admin.getByTestId("game-row").filter({ hasText: GENERIC_GAME })).toBeVisible()
  })

  await admin.context().close()
})

setup("a copy of the database is kept for every test", async ({ request }) => {
  await keepCopy()
  // Board Keeper was stopped for the copy, and is back.
  await expect(await request.get("/actuator/health")).toBeOK()
})

/**
 * Saves how an Account is signed in, for the tests that start as it: its cookies, and nothing
 * else. What Board Keeper keeps on the device, such as its copy of who is signed in and in which
 * language, belongs to the moment of the setup. Every test starts with a device that has kept
 * nothing, like a phone that has just signed in.
 */
async function saveSignIn(page: Page, path: string) {
  const { cookies } = await page.context().storageState()
  await mkdir(dirname(path), { recursive: true })
  await writeFile(path, JSON.stringify({ cookies, origins: [] }))
}

async function addPlayer(page: Page, name: string) {
  await page.getByTestId("add-player").click()
  await page.getByTestId("player-name").fill(name)
  await page.getByTestId("save-player").click()
  await expect(page.getByTestId("player-row").filter({ hasText: name })).toBeVisible()
}
