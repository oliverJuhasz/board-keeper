import { http, HttpResponse } from "msw"
import type {
  Account,
  CurrentAccount,
  Game,
  GameModule,
  Invite,
  Play,
  Player,
  Role,
} from "@/api/client"
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
    "CREATE_GAMES",
    "EDIT_GAMES",
    "REMOVE_GAMES",
    "CREATE_PLAYS",
    "CHANGE_PLAYS_RECORDED",
    "CHANGE_PLAYS_TAKEN_PART_IN",
    "CHANGE_ANY_PLAY",
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
  permissions: [
    "CREATE_PLAYERS",
    "CREATE_GAMES",
    "EDIT_GAMES",
    "CREATE_PLAYS",
    "CHANGE_PLAYS_RECORDED",
    "CHANGE_PLAYS_TAKEN_PART_IN",
  ],
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

/** A competitive Game recorded in points, where the highest score wins. */
export const calico: Game = {
  id: "0193a000-0000-7000-8000-0000000000e1",
  name: "Calico",
  gameModuleId: genericModule.id,
  cooperative: false,
  recordingMode: "POINTS",
  lowestScoreWins: false,
}

/** A Cooperative Game recorded as won or lost. */
export const pandemic: Game = {
  id: "0193a000-0000-7000-8000-0000000000e2",
  name: "Pandemic",
  gameModuleId: genericModule.id,
  cooperative: true,
  recordingMode: "WINNER_ONLY",
  lowestScoreWins: false,
}

/**
 * What the Games page, the start page, loads. The list is kept here, so a test's own handlers
 * can change it and the page sees the change when it loads the list again.
 */
export function gamesAre(games: Game[]) {
  const state = { games: [...games] }
  server.use(http.get("*/api/games", () => HttpResponse.json(state.games)))
  return state
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

/** A Role an Admin created. */
export const scorekeeperRole: Role = {
  id: "0193a000-0000-7000-8000-0000000000c3",
  name: "Scorekeeper",
  permissions: ["CREATE_PLAYS", "EDIT_PLAYERS"],
}

export const annaAccount: Account = {
  id: anna.id,
  username: "anna",
  playerId: anna.playerId,
  roleIds: [adminRole.id],
  disabled: false,
}

export const benceAccount: Account = {
  id: bence.id,
  username: "bence",
  playerId: bence.playerId,
  roleIds: [memberRole.id],
  disabled: false,
}

/** What the Accounts and Roles pages load. As with playersAre, tests can change the lists. */
export function accountsAre(accounts: Account[], roles: Role[] = [adminRole, memberRole]) {
  const state = { accounts: [...accounts], roles: [...roles] }
  server.use(
    http.get("*/api/accounts", () => HttpResponse.json(state.accounts)),
    http.get("*/api/roles", () => HttpResponse.json(state.roles)),
    http.get("*/api/players", () => HttpResponse.json([annaPlayer, bencePlayer, doraPlayer])),
  )
  return state
}

/** What the page for recording a Play loads besides the Games: the Players and the Game Modules' texts. */
export function playCanBeEntered(gameModuleTexts: Record<string, string> = {}) {
  server.use(
    http.get("*/api/players", () => HttpResponse.json([annaPlayer, bencePlayer, doraPlayer])),
    http.get("*/api/game-modules/texts/:language", () => HttpResponse.json(gameModuleTexts)),
  )
}

/** A competitive Play of Calico in points, recorded by Bence: Anna won, Dora came second. */
export const calicoPlay: Play = {
  id: "0193a000-0000-7000-8000-0000000000f1",
  game: { id: calico.id, name: calico.name },
  playedOn: "2026-03-14",
  location: "At Dora's",
  notes: "Dora's first play.",
  recordingMode: "POINTS",
  setup: { cooperative: "false", lowestScoreWins: "false" },
  cooperative: false,
  results: [
    {
      player: doraPlayer,
      scores: { total: 61 },
      markedWinner: false,
      total: 61,
      placing: 2,
      won: false,
    },
    {
      player: annaPlayer,
      scores: { total: 74 },
      markedWinner: false,
      total: 74,
      placing: 1,
      won: true,
    },
  ],
  recordedBy: bencePlayer,
  recordedAt: "2026-03-14T12:00:00Z",
  canChange: true,
}

/** A Cooperative Play of Pandemic that was lost, with a score for the group. */
export const pandemicPlay: Play = {
  id: "0193a000-0000-7000-8000-0000000000f2",
  game: { id: pandemic.id, name: pandemic.name },
  playedOn: "2026-03-15",
  recordingMode: "POINTS",
  setup: { cooperative: "true", lowestScoreWins: "false" },
  cooperative: true,
  group: { won: false, scores: { total: 17 }, total: 17 },
  results: [
    { player: annaPlayer, scores: {}, markedWinner: false, won: false },
    { player: bencePlayer, scores: {}, markedWinner: false, won: false },
  ],
  recordedBy: annaPlayer,
  recordedAt: "2026-03-15T12:00:00Z",
  canChange: true,
}

/** Answers a request for this Play. */
export function playIs(play: Play) {
  server.use(http.get(`*/api/plays/${play.id}`, () => HttpResponse.json(play)))
}

/**
 * What the Plays page loads: the play history, and the Games and Players for its filters.
 * Every request for the history is remembered, with the filters it asked for.
 */
export function historyIs(...pages: Play[][]) {
  const asked: URLSearchParams[] = []
  server.use(
    http.get("*/api/plays", ({ request }) => {
      const query = new URL(request.url).searchParams
      asked.push(query)
      // "after" is empty for the first page, and "page-2", "page-3" and so on after that.
      const index = query.get("after") ? Number(query.get("after")?.slice(5)) - 1 : 0
      const next = index + 1 < pages.length ? `page-${index + 2}` : undefined
      return HttpResponse.json({ plays: pages[index] ?? [], next })
    }),
    http.get("*/api/games", () => HttpResponse.json([calico, pandemic])),
    http.get("*/api/players", () => HttpResponse.json([annaPlayer, bencePlayer, doraPlayer])),
  )
  return asked
}
