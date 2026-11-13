import { http, HttpResponse } from "msw"
import { setupServer } from "msw/node"
import { gameModules, gameModuleTexts } from "./gameModules"

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
)
