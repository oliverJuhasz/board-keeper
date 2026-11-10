import { http, HttpResponse } from "msw"
import type { CurrentAccount, Game, GameModule, Invite, Player, Role } from "@/api/client"
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

export const adminRole: Role = {
  id: "0193a000-0000-7000-8000-0000000000c1",
  name: "Admin",
  builtIn: "ADMIN",
  permissions: anna.permissions,
}

export const memberRole: Role = {
  id: "0193a000-0000-7000-8000-0000000000c2",
  name: "Member",
  builtIn: "MEMBER",
  permissions: bence.permissions,
}

export const annaPlayer: Player = { id: anna.playerId, name: "Anna" }
export const bencePlayer: Player = { id: bence.playerId, name: "Bence" }
/** A Player without an Account. */
export const doraPlayer: Player = { id: "0193a000-0000-7000-8000-0000000000b3", name: "Dora" }

/**
 * What the Players page loads. The lists are kept here, so a test's own handlers can change
 * them and the page sees the change when it loads them again.
 */
export function playersAre(players: Player[], claimedPlayerIds: string[], invites: Invite[] = []) {
  const state = { players: [...players], claimedPlayerIds, invites: [...invites] }
  server.use(
    http.get("*/api/players", () => HttpResponse.json(state.players)),
    http.get("*/api/claims", () => HttpResponse.json({ playerIds: state.claimedPlayerIds })),
    http.get("*/api/invites", () => HttpResponse.json(state.invites)),
    http.get("*/api/roles", () => HttpResponse.json([adminRole, memberRole])),
  )
  return state
}
