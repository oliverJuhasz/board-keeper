import { screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { beforeEach, expect, test, vi } from "vitest"
import type { About } from "@/api/client"
import { appCommit, reloadIntoNewestVersion } from "@/lib/appUpdate"
import { bence, signedInAs } from "@/test/api"
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

  expect(await screen.findByText("Version 1.2.3")).toBeInTheDocument()
  expect(screen.getByText("sha-1221b00 · 14 November 2026")).toBeInTheDocument()
  expect(screen.getByRole("link", { name: "What's new in 1.2.3" })).toHaveAttribute(
    "href",
    about.releaseNotes,
  )
})

test("shows a build that is no release as a Snapshot, past the newest release", async () => {
  vi.mocked(appCommit).mockReturnValue(OTHER_COMMIT)
  aboutIs(snapshot)
  renderApp("/about")

  expect(await screen.findByText("Snapshot")).toBeInTheDocument()
  expect(
    screen.getByText("sha-9f8e7d6 · 2 December 2026 · 4 changes after 1.2.3"),
  ).toBeInTheDocument()
  expect(screen.queryByRole("link", { name: /What's new/ })).not.toBeInTheDocument()
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

  expect(
    await screen.findByText(
      "sha-9f8e7d6 · 2 December 2026 · no release yet · with uncommitted changes",
    ),
  ).toBeInTheDocument()
})

test("says that the version is unknown when the build had no git", async () => {
  aboutIs({
    uncommittedChanges: false,
    sourceCode: "https://github.com/oliverJuhasz/board-keeper/tree/HEAD",
  })
  renderApp("/about")

  expect(await screen.findByText("Version unknown")).toBeInTheDocument()
  expect(
    screen.getByText("This copy was built without Git, so it can't tell which version it is."),
  ).toBeInTheDocument()
})

test("links to the repository and to the code of the running version, in a new tab", async () => {
  renderApp("/about")

  const repository = await screen.findByRole("link", {
    name: "github.com/oliverJuhasz/board-keeper",
  })
  expect(repository).toHaveAttribute("href", about.repository)
  expect(repository).toHaveAttribute("target", "_blank")
  expect(screen.getByRole("link", { name: "The code of exactly this version" })).toHaveAttribute(
    "href",
    about.sourceCode,
  )
})

test("a copy that names no repository and no page for its releases has no such links", async () => {
  aboutIs({ ...about, repository: undefined, releaseNotes: undefined })
  renderApp("/about")

  expect(
    await screen.findByRole("link", { name: "The code of exactly this version" }),
  ).toBeVisible()
  expect(screen.queryByRole("link", { name: /github\.com/ })).not.toBeInTheDocument()
  expect(screen.queryByRole("link", { name: /What's new/ })).not.toBeInTheDocument()
})

test("has a word from the maker, and the licence", async () => {
  renderApp("/about")

  expect(
    await screen.findByText(
      "I built Board Keeper to make keeping track of scores easier. Have great plays!",
    ),
  ).toBeInTheDocument()
  expect(screen.getByText("— Oliver")).toBeInTheDocument()
  expect(
    screen.getByText(/free software under the GNU Affero General Public License/),
  ).toBeVisible()
  expect(screen.getByRole("link", { name: "The licence's text" })).toHaveAttribute(
    "href",
    "https://www.gnu.org/licenses/agpl-3.0.html",
  )
  expect(screen.getByRole("link", { name: "Licences of the libraries it uses" })).toHaveAttribute(
    "href",
    "/third-party-licenses.txt",
  )
})

test("says when this device still has another version of the app, and reloads into the new one", async () => {
  vi.mocked(appCommit).mockReturnValue(OTHER_COMMIT)
  renderApp("/about")

  expect(
    await screen.findByText("This device still has another version of the app: sha-9f8e7d6."),
  ).toHaveRole("status")

  await userEvent.click(screen.getByRole("button", { name: "Reload" }))
  expect(reloadIntoNewestVersion).toHaveBeenCalledOnce()
})

test("says nothing about this device while it has the server's version", async () => {
  renderApp("/about")

  const version = (await screen.findByText("Version 1.2.3")).closest("[data-slot=card]")
  expect(within(version as HTMLElement).queryByRole("button")).not.toBeInTheDocument()
  expect(screen.queryByRole("status")).not.toBeInTheDocument()
})

test("is in Dutch and Hungarian too", async () => {
  signedInAs({ ...bence, language: "hu" })
  renderApp("/about")

  expect(await screen.findByRole("heading", { name: "A Board Keeperről" })).toBeInTheDocument()
  expect(await screen.findByText("Verzió: 1.2.3")).toBeInTheDocument()
  expect(screen.getByText(/Sok jó partit!/)).toBeInTheDocument()
})
