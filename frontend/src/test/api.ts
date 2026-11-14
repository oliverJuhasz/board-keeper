import { http, HttpResponse } from "msw"
import { expect } from "vitest"
import type {
  Account,
  CurrentAccount,
  Game,
  Invite,
  Play,
  PlayPreview,
  PlayPreviewRequest,
  Player,
  Role,
} from "@/api/client"
import {
  calicoModule,
  duelModule,
  genericModule,
  sevenWondersModule,
  spiritIslandModule,
  wyrmspanModule,
} from "./gameModules"
import { server } from "./server"

// Ready-made answers of the pretend backend, for tests to combine.

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
export const azul: Game = {
  id: "0193a000-0000-7000-8000-0000000000e1",
  name: "Azul",
  gameModuleId: genericModule.id,
  moduleKey: "generic",
  hasOwnModule: false,
  cooperative: false,
  recordingMode: "POINTS",
  lowestScoreWins: false,
}

/** A Game with a Game Module of its own: it came with Board Keeper and has its own score pad. */
export const calico: Game = {
  id: "0193a000-0000-7000-8000-0000000000e4",
  name: "Calico",
  gameModuleId: calicoModule.id,
  moduleKey: "calico",
  hasOwnModule: true,
  cooperative: false,
  recordingMode: "POINTS",
  lowestScoreWins: false,
}

/** A Game with a Game Module of its own that can be played alone against the Automa. */
export const wyrmspan: Game = {
  id: "0193a000-0000-7000-8000-0000000000e5",
  name: "Wyrmspan",
  gameModuleId: wyrmspanModule.id,
  moduleKey: "wyrmspan",
  hasOwnModule: true,
  cooperative: false,
  recordingMode: "POINTS",
  lowestScoreWins: false,
}

/** A Game that is won on points or outright, with Expansions to play with. */
export const duel: Game = {
  id: "0193a000-0000-7000-8000-0000000000e6",
  name: "7 Wonders Duel",
  gameModuleId: duelModule.id,
  moduleKey: "seven-wonders-duel",
  hasOwnModule: true,
  cooperative: false,
  recordingMode: "POINTS",
  lowestScoreWins: false,
}

/** A Game in which each Player has a Player Setup: their Wonder and its side. */
export const sevenWonders: Game = {
  id: "0193a000-0000-7000-8000-0000000000e7",
  name: "7 Wonders",
  gameModuleId: sevenWondersModule.id,
  moduleKey: "seven-wonders",
  hasOwnModule: true,
  cooperative: false,
  recordingMode: "POINTS",
  lowestScoreWins: false,
}

/** A Cooperative Game with a module of its own, which works out the group's score. */
export const spiritIsland: Game = {
  id: "0193a000-0000-7000-8000-0000000000e8",
  name: "Spirit Island",
  gameModuleId: spiritIslandModule.id,
  moduleKey: "spirit-island",
  hasOwnModule: true,
  cooperative: true,
  recordingMode: "WINNER_ONLY",
  lowestScoreWins: false,
}

/** A Cooperative Game recorded as won or lost. */
export const pandemic: Game = {
  id: "0193a000-0000-7000-8000-0000000000e2",
  name: "Pandemic",
  gameModuleId: genericModule.id,
  moduleKey: "generic",
  hasOwnModule: false,
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

/** What the page for recording a Play loads besides the Games: the Players. */
export function playCanBeEntered() {
  server.use(
    http.get("*/api/players", () => HttpResponse.json([annaPlayer, bencePlayer, doraPlayer])),
  )
}

/**
 * Answers a request to work out how a Play would end, the way the real backend would for a
 * Game that adds up its rows: the highest total is first, and Players level in first place
 * are a tie for the recorder. Every request is remembered.
 */
export function playsCanBePreviewed() {
  const asked: PlayPreviewRequest[] = []
  server.use(
    http.post("*/api/plays/preview", async ({ request }) => {
      const body = (await request.json()) as PlayPreviewRequest
      asked.push(body)
      const totals = body.players.map((player) =>
        Object.values(player.scores ?? {}).reduce((sum, value) => sum + value, 0),
      )
      const best = Math.max(...totals)
      const tied = body.players.filter((_, index) => totals[index] === best)
      const preview: PlayPreview = {
        cooperative: false,
        results: body.players.map((player, index) => ({
          playerId: player.playerId,
          total: totals[index],
          placing: 1 + totals.filter((total) => total > totals[index]).length,
          won: totals[index] === best,
        })),
        tieForRecorder: tied.length > 1 ? tied.map((player) => player.playerId) : [],
      }
      return HttpResponse.json(preview)
    }),
  )
  return asked
}

/** A competitive Play of Azul in points, recorded by Bence: Anna won, Dora came second. */
export const azulPlay: Play = {
  id: "0193a000-0000-7000-8000-0000000000f1",
  game: { id: azul.id, name: azul.name },
  gameModuleId: genericModule.id,
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
      setup: {},
      markedWinner: false,
      total: 61,
      placing: 2,
      won: false,
    },
    {
      player: annaPlayer,
      scores: { total: 74 },
      setup: {},
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
  gameModuleId: genericModule.id,
  playedOn: "2026-03-15",
  recordingMode: "POINTS",
  setup: { cooperative: "true", lowestScoreWins: "false" },
  cooperative: true,
  group: { won: false, scores: { total: 17 }, total: 17 },
  results: [
    { player: annaPlayer, scores: {}, setup: {}, markedWinner: false, won: false },
    { player: bencePlayer, scores: {}, setup: {}, markedWinner: false, won: false },
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
    http.get("*/api/games", () => HttpResponse.json([azul, pandemic])),
    http.get("*/api/players", () => HttpResponse.json([annaPlayer, bencePlayer, doraPlayer])),
  )
  return asked
}

/**
 * The id the form gives a new Play: a version 7 UUID, made fresh each time, so a test can only
 * check its form.
 */
export const aNewPlayId = expect.stringMatching(
  /^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/,
) as string
