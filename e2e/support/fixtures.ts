import {
  test as base,
  expect,
  type Browser,
  type BrowserContextOptions,
  type Page,
  type TestInfo,
} from "@playwright/test"
import { restoreCopy } from "./installation.ts"
import type { paths } from "./schema.d.ts"

/**
 * The `test` every journey uses: Playwright's own, with fixtures added. A fixture is something a
 * test asks for by name in its arguments, such as `page`; Playwright sets it up before the test
 * and tidies it up after. https://playwright.dev/docs/test-fixtures explains them.
 */
export const test = base.extend<{
  freshDatabase: void
  api: Api
  pageAs: (storageState: StorageState) => Promise<Page>
}>({
  // Puts the copy of the database back before every test, so each starts from the same
  // Installation (ADR 0024). "auto" runs it for every test without being asked for.
  freshDatabase: [
    // Playwright reads which fixtures a fixture needs from its first argument, so it must be an
    // object pattern even when, as here, it needs none.
    async ({}, use) => {
      await restoreCopy()
      await use()
    },
    { auto: true },
  ],

  // For creating many things at once through the API, as the Admin. Everything else a test
  // makes through the screens, like a person.
  api: async ({ request }, use) => {
    // Board Keeper refuses a change without the token from its XSRF-TOKEN cookie in a header,
    // as the frontend sends it (see "Forged requests" in docs/development.md).
    const cookies = (await request.storageState()).cookies
    const token = cookies.find((cookie) => cookie.name === "XSRF-TOKEN")?.value ?? ""

    await use({
      async get(path) {
        const response = await request.get(path)
        await expect(response, await response.text()).toBeOK()
        return await response.json()
      },
      async post(path, body) {
        const response = await request.post(path, {
          data: body,
          headers: { "X-XSRF-TOKEN": token },
        })
        await expect(response, await response.text()).toBeOK()
        return await response.json()
      },
    })
  },

  // A second browser, signed in as another Account, for a journey with two people in it. It
  // has the screen size and touch screen of the project the test runs in.
  pageAs: async ({ browser }, use, testInfo) => {
    const pages: Page[] = []
    await use(async (storageState) => {
      const page = await newPage(browser, testInfo, storageState)
      pages.push(page)
      return page
    })
    for (const page of pages) {
      await page.context().close()
    }
  },
})

export { expect }

/** How a page is signed in: a file the setup project saved, or SIGNED_OUT. */
type StorageState = BrowserContextOptions["storageState"]

/** Opens a page in a browser of its own, as in the test's project, with the given sign-in. */
async function newPage(browser: Browser, testInfo: TestInfo, storageState: StorageState) {
  const {
    viewport,
    userAgent,
    deviceScaleFactor,
    isMobile,
    hasTouch,
    locale,
    timezoneId,
    baseURL,
  } = testInfo.project.use
  const context = await browser.newContext({
    viewport,
    userAgent,
    deviceScaleFactor,
    isMobile,
    hasTouch,
    locale,
    timezoneId,
    baseURL,
    storageState,
  })
  return await context.newPage()
}

/**
 * The parts of the API a test may call, typed from api/openapi.json (`npm run generate:api`
 * writes support/schema.d.ts). A change to the API that breaks a test then fails to compile.
 */
type Api = {
  get<Path extends PathWith<"get">>(path: Path): Promise<Answer<Path, "get">>
  post<Path extends PathWith<"post">>(
    path: Path,
    body: Body<Path, "post">,
  ): Promise<Answer<Path, "post">>
}

type Method = "get" | "post"
/** The paths that answer to a method, such as every path that takes a POST. */
type PathWith<M extends Method> = {
  [Path in Paths]: paths[Path][M] extends { responses: unknown } ? Path : never
}[Paths]
type Paths = Extract<keyof paths, string>
/** What a request sends as JSON. */
type Body<Path extends keyof paths, M extends Method> = paths[Path][M] extends {
  requestBody?: { content: { "application/json": infer Sent } }
}
  ? Sent
  : never
/** What a successful request answers with. */
type Answer<Path extends keyof paths, M extends Method> = paths[Path][M] extends {
  responses: { 200: { content: { "application/json": infer Answered } } }
}
  ? Answered
  : never
