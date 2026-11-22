import { fileURLToPath } from "node:url"

/**
 * Who and what every test finds in Board Keeper. The setup project (installation.setup.ts) creates
 * them through the screens, and a copy of the database is put back before each test.
 */

/** The password of both Accounts. */
export const PASSWORD = "correct horse battery staple"

/**
 * Where the setup project saves how an Account is signed in: its cookies and the browser's
 * storage, which Playwright calls the "storage state". A test that starts from one is signed in.
 */
const stateFile = (name: string) => fileURLToPath(new URL(`../.auth/${name}.json`, import.meta.url))

/** The first Admin, created through the Setup Link, and their Player. */
export const ADMIN = { username: "admin", player: "Dora", state: stateFile("admin") }

/** A Member, who registered through an Invite, and their Player. */
export const MEMBER = { username: "erik", player: "Erik", state: stateFile("member") }

/** How a test that starts signed out is told so. */
export const SIGNED_OUT = { cookies: [], origins: [] }

/** Players without an Account. */
export const PLAYERS = ["Anna", "Bence", "Csilla"] as const

/** A Game without a Game Module of its own: competitive, in points, the highest score wins. */
export const GENERIC_GAME = "Azul"
