import { http, HttpResponse } from "msw"
import type { CurrentAccount, Game, GameModule } from "@/api/client"
import { server } from "./server"

// Ready-made answers of the pretend backend, for tests to combine.

export const genericModule: GameModule = {
  id: "38b119d2-8f9f-4e20-8068-cba305c06cdd",
  key: "generic",
}

/** An Admin, who holds every Permission. */
export const anna: CurrentAccount = {
  id: "0193a000-0000-7000-8000-0000000000a1",
  username: "anna",
  playerId: "0193a000-0000-7000-8000-0000000000b1",
  playerName: "Anna",
  language: "en",
  permissions: [
    "CREATE_PLAYERS",
    "EDIT_PLAYERS",
    "REMOVE_PLAYERS",
    "CREATE_PLAYS",
    "MANAGE_INVITES",
    "MANAGE_ACCOUNTS",
    "MANAGE_ROLES",
  ],
}

/** A Member, with only the Permissions every Member starts with. */
export const bence: CurrentAccount = {
  id: "0193a000-0000-7000-8000-0000000000a2",
  username: "bence",
  playerId: "0193a000-0000-7000-8000-0000000000b2",
  playerName: "Bence",
  language: "en",
  permissions: ["CREATE_PLAYERS", "CREATE_PLAYS"],
}

const gameModuleTexts: Record<string, Record<string, string>> = {
  en: { "generic.name": "Generic" },
  nl: { "generic.name": "Algemeen" },
  hu: { "generic.name": "Általános" },
}

export function signedInAs(account: CurrentAccount) {
  server.use(http.get("*/api/session", () => HttpResponse.json({ account })))
}

export function signedOut() {
  server.use(
    http.get("*/api/session", () => HttpResponse.json({})),
    http.get("*/api/setup", () => HttpResponse.json({ required: false })),
  )
}

/** What the start page loads. */
export function gamesAre(games: Game[], gameModules: GameModule[] = [genericModule]) {
  server.use(
    http.get("*/api/games", () => HttpResponse.json(games)),
    http.get("*/api/game-modules", () => HttpResponse.json(gameModules)),
    http.get("*/api/game-modules/texts/:language", ({ params }) =>
      HttpResponse.json(gameModuleTexts[params.language as string]),
    ),
  )
}

/** An error answer in the backend's format. */
export function problem(status: number, code: string) {
  return HttpResponse.json({ status, code }, { status })
}
