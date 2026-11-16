import { http, HttpResponse } from "msw"
import { setupServer } from "msw/node"
import type { About } from "@/api/client"
import { gameModules, gameModuleTexts } from "./gameModules"

/** What the backend says about the running version: here, a release. */
export const about: About = {
  version: "1.2.3",
  commit: "1221b004748ae4f76d42d91dcdba7abb5890b2b7",
  committedAt: "2026-11-14T22:40:52Z",
  uncommittedChanges: false,
  repository: "https://github.com/oliverJuhasz/board-keeper",
  sourceCode:
    "https://github.com/oliverJuhasz/board-keeper/tree/1221b004748ae4f76d42d91dcdba7abb5890b2b7",
  releaseNotes: "https://github.com/oliverJuhasz/board-keeper/releases/tag/1.2.3",
}

/**
 * A stand-in for the backend in tests. Mock Service Worker (MSW) catches the requests the
 * app makes and answers them with whatever the test has set up through `server.use(...)`.
 *
 * The installed Game Modules and their texts are answered for every test, because many pages
 * need them and they are part of the application, not of a test's situation.
 */
export const server = setupServer(
  http.get("*/api/game-modules", () => HttpResponse.json(gameModules)),
  http.get("*/api/game-modules/texts/:language", () => HttpResponse.json(gameModuleTexts)),
  // The start page asks when each Game was last played, and for recent Plays to play again.
  // Unless a test says otherwise, nothing has been played yet.
  http.get("*/api/plays/last-played", () => HttpResponse.json([])),
  http.get("*/api/plays", () => HttpResponse.json({ plays: [] })),
  // Which version is running and where its source code is, for the About page.
  http.get("*/api/about", () => HttpResponse.json(about)),
  // The settings page lists the Account's API Keys. Unless a test says otherwise, there are none.
  http.get("*/api/account/api-keys", () => HttpResponse.json([])),
)
