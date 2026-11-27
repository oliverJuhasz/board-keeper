import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { beforeEach, expect, test, vi } from "vitest"
import type { About } from "@/api/client"
import { appCommit, reloadIntoNewestVersion } from "@/lib/appUpdate"
import { bence, signedInAs } from "@/test/api"
import { page } from "@/test/queries"
import { renderApp } from "@/test/renderApp"
import { about, server } from "@/test/server"

// The commit the app on this device was built from is set by each test, and reloading is only
// watched: a test has no service worker and no page to reload.
vi.mock("@/lib/appUpdate", () => ({
  appCommit: vi.fn(),
  reloadIntoNewestVersion: vi.fn(() => Promise.resolve()),
}))

const OTHER_COMMIT = "9f8e7d6c5b4a39281706f5e4d3c2b1a098765432"

/** A build from a commit that is no release's: four commits past 1.2.3. */
const snapshot: About = {
  commit: OTHER_COMMIT,
  committedAt: "2026-12-02T09:15:00Z",
  uncommittedChanges: false,
  latestRelease: "1.2.3",
  changesSinceRelease: 4,
  repository: "https://github.com/oliverJuhasz/board-keeper",
  sourceCode: `https://github.com/oliverJuhasz/board-keeper/tree/${OTHER_COMMIT}`,
}

function aboutIs(answer: About) {
  server.use(http.get("*/api/about", () => HttpResponse.json(answer)))
}

beforeEach(() => {
  signedInAs(bence)
  // The device has the same version as the server, unless a test says otherwise.
  vi.mocked(appCommit).mockReturnValue(about.commit)
  vi.mocked(reloadIntoNewestVersion).mockClear()
})

test("shows a release with its number, its commit and what is new in it", async () => {
  renderApp("/about")

  expect(await page.findByTestId("version-title")).toHaveTextContent("Version 1.2.3")
  expect(page.getByTestId("version-details")).toHaveTextContent("sha-1221b00 · 14 November 2026")
  expect(page.getByTestId("whats-new")).toHaveTextContent("What's new in 1.2.3")
  expect(page.getByTestId("whats-new")).toHaveAttribute("href", about.releaseNotes)
})

test("shows a build that is no release as a Snapshot, past the newest release", async () => {
  vi.mocked(appCommit).mockReturnValue(OTHER_COMMIT)
  aboutIs(snapshot)
  renderApp("/about")

  expect(await page.findByTestId("version-title")).toHaveTextContent("Snapshot")
  expect(page.getByTestId("version-details")).toHaveTextContent(
    "sha-9f8e7d6 · 2 December 2026 · 4 changes after 1.2.3",
  )
  expect(page.queryByTestId("whats-new")).not.toBeInTheDocument()
})

test("says so when there is no release yet, and when the code was changed after its commit", async () => {
  vi.mocked(appCommit).mockReturnValue(OTHER_COMMIT)
  aboutIs({
    ...snapshot,
    latestRelease: undefined,
    changesSinceRelease: undefined,
    uncommittedChanges: true,
  })
  renderApp("/about")

  expect(await page.findByTestId("version-details")).toHaveTextContent(
    "sha-9f8e7d6 · 2 December 2026 · no release yet · with uncommitted changes",
  )
})

test("says that the version is unknown when the build had no git", async () => {
  aboutIs({
    uncommittedChanges: false,
    sourceCode: "https://github.com/oliverJuhasz/board-keeper/tree/HEAD",
  })
  renderApp("/about")

  expect(await page.findByTestId("version-title")).toHaveTextContent("Version unknown")
  expect(page.getByTestId("version-details")).toHaveTextContent(
    "This copy was built without Git, so it can't tell which version it is.",
  )
})

test("links to the repository and to the code of the running version, in a new tab", async () => {
  renderApp("/about")

  const repository = await page.findByTestId("repository")
  expect(repository).toHaveTextContent("github.com/oliverJuhasz/board-keeper")
  expect(repository).toHaveAttribute("href", about.repository)
  expect(repository).toHaveAttribute("target", "_blank")
  expect(page.getByTestId("source-of-this-version")).toHaveAttribute("href", about.sourceCode)
})

test("a copy that names no repository and no page for its releases has no such links", async () => {
  aboutIs({ ...about, repository: undefined, releaseNotes: undefined })
  renderApp("/about")

  expect(await page.findByTestId("source-of-this-version")).toHaveTextContent(
    "The code of exactly this version",
  )
  expect(page.queryByTestId("repository")).not.toBeInTheDocument()
  expect(page.queryByTestId("whats-new")).not.toBeInTheDocument()
})

test("has a word from the maker, and the licence", async () => {
  renderApp("/about")

  expect(await page.findByTestId("about-message")).toHaveTextContent(
    "I built Board Keeper to make keeping track of scores easier. Have great plays!",
  )
  expect(page.getByTestId("about-signature")).toHaveTextContent("— Oliver")
  expect(page.getByTestId("licence")).toHaveTextContent(
    /free software under the GNU Affero General Public License/,
  )
  expect(page.getByTestId("licence")).toBeVisible()
  expect(page.getByTestId("licence-text")).toHaveTextContent("The licence's text")
  expect(page.getByTestId("licence-text")).toHaveAttribute(
    "href",
    "https://www.gnu.org/licenses/agpl-3.0.html",
  )
  expect(page.getByTestId("library-licences")).toHaveTextContent(
    "Licences of the libraries it uses",
  )
  expect(page.getByTestId("library-licences")).toHaveAttribute("href", "/third-party-licenses.txt")
})

test("says when this device still has another version of the app, and reloads into the new one", async () => {
  vi.mocked(appCommit).mockReturnValue(OTHER_COMMIT)
  renderApp("/about")

  const device = await page.findByTestId("device-behind")
  expect(device).toHaveTextContent("This device still has another version of the app: sha-9f8e7d6.")
  // Screen readers announce it as it appears.
  expect(device).toHaveRole("status")

  await userEvent.click(page.getByTestId("reload-app"))
  expect(reloadIntoNewestVersion).toHaveBeenCalledOnce()
})

test("says nothing about this device while it has the server's version", async () => {
  renderApp("/about")

  expect(await page.findByTestId("version-title")).toHaveTextContent("Version 1.2.3")
  expect(page.queryByTestId("reload-app")).not.toBeInTheDocument()
  expect(page.queryByTestId("device-behind")).not.toBeInTheDocument()
})

test("is in Dutch and Hungarian too", async () => {
  signedInAs({ ...bence, language: "hu" })
  renderApp("/about")

  expect(await page.findByTestIdWith("page-title", "A Board Keeperről")).toBeInTheDocument()
  expect(await page.findByTestIdWith("version-title", "Verzió: 1.2.3")).toBeInTheDocument()
  expect(page.getByTestId("about-message")).toHaveTextContent(/Sok jó partit!/)
})
