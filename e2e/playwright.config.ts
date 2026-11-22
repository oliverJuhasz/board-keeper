import { defineConfig, devices } from "@playwright/test"
import { ADMIN } from "./support/cast.ts"
import { url } from "./support/installation.ts"

/**
 * The end-to-end tests: Board Keeper's container image, driven in a real browser (ADR 0024).
 * https://playwright.dev/docs/test-configuration explains each setting.
 */
export default defineConfig({
  testDir: "./tests",
  // Starts the Installation before everything, and removes it after everything.
  globalSetup: "./support/global-setup.ts",
  globalTeardown: "./support/global-teardown.ts",
  // One test at a time: every test starts by putting the same database back, so two tests
  // can't share the one Installation at once.
  workers: 1,
  // On CI, a test left marked with test.only fails the run instead of silently skipping the rest.
  forbidOnly: !!process.env.CI,
  // A test that fails on CI is run once more; if it passes then, the report marks it "flaky".
  // That is a bug to fix, not one to live with.
  retries: process.env.CI ? 1 : 0,
  // "list" prints each test as it runs. "html" writes a report with a trace of each failure,
  // which `npm run report` opens.
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: url,
    // Board Keeper chooses its language from the browser's: these tests read its English texts.
    locale: "en-GB",
    timezoneId: "Europe/Amsterdam",
    // A trace records every step of a test: the page before and after, the network and the
    // console. Kept only for a test that failed, as it is large.
    trace: "retain-on-failure",
  },
  projects: [
    // Creates the first Admin and the Players and Game every test finds, and keeps a copy of the
    // database (tests/installation.setup.ts). The other projects depend on it, so it runs first.
    { name: "setup", testMatch: /.*\.setup\.ts/ },
    {
      name: "desktop",
      use: { ...devices["Desktop Chrome"], storageState: ADMIN.state },
      dependencies: ["setup"],
    },
    {
      // The screen size, touch screen and browser of a phone.
      name: "phone",
      use: { ...devices["Pixel 7"], storageState: ADMIN.state },
      dependencies: ["setup"],
    },
  ],
})
