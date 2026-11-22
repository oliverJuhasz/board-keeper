import { execFile } from "node:child_process"
import { fileURLToPath } from "node:url"
import { promisify } from "node:util"

/**
 * The Installation the tests run against: the container image with PostgreSQL, started through
 * the example compose file that self-hosters copy (ADR 0024). Everything here runs `docker compose`
 * from outside; nothing in the image is there for the tests.
 */

const run = promisify(execFile)

/** The port Board Keeper answers on, away from 8080, where a development backend may run. */
const port = process.env.BOARD_KEEPER_PORT ?? "8180"

/** Where the tests find Board Keeper. */
export const url = `http://localhost:${port}`

const composeFile = fileURLToPath(new URL("../../deploy/compose.yaml", import.meta.url))

/** The database the application uses, and the copy of it that every test starts from. */
const database = "boardkeeper"
const copy = "boardkeeper_clean"

/**
 * What the compose file reads from the environment. The password only guards a database that
 * lives as long as one run of the tests.
 */
const environment = {
  ...process.env,
  BOARD_KEEPER_IMAGE: process.env.BOARD_KEEPER_IMAGE ?? "board-keeper:local",
  BOARD_KEEPER_PORT: port,
  POSTGRES_PASSWORD: "only-for-end-to-end-tests",
  // Passkeys belong to one address (journey 9). Browsers allow them over plain http on localhost.
  BOARDKEEPER_URL: url,
}

/** Runs `docker compose` on the compose file, under a project name of the tests' own. */
async function compose(...args: string[]) {
  const { stdout } = await run(
    "docker",
    ["compose", "--file", composeFile, "--project-name", "board-keeper-e2e", ...args],
    { env: environment, maxBuffer: 16 * 1024 * 1024 },
  )
  return stdout
}

/** Runs SQL statements one by one in the database container, as its administrator. */
async function sql(...statements: string[]) {
  await compose(
    "exec",
    "-T",
    "db",
    "psql",
    "--username",
    "boardkeeper",
    // Connected to another database, because the application's own is dropped and copied.
    "--dbname",
    "postgres",
    "--set",
    "ON_ERROR_STOP=1",
    ...statements.flatMap((statement) => ["--command", statement]),
  )
}

/** Waits until Board Keeper says it is up, after the container has started. */
async function untilUp() {
  const deadline = Date.now() + 120_000
  while (Date.now() < deadline) {
    try {
      const response = await fetch(`${url}/actuator/health`)
      if (response.ok) {
        return
      }
    } catch {
      // Not answering yet.
    }
    await new Promise((resolve) => setTimeout(resolve, 500))
  }
  throw new Error(`Board Keeper didn't start at ${url} within two minutes.`)
}

/** Starts Board Keeper and its database, with no data at all. */
export async function start() {
  await compose("up", "--detach", "--wait")
  await untilUp()
}

/** Stops both containers and deletes the database with everything in it. */
export async function remove() {
  await compose("down", "--volumes", "--remove-orphans")
}

/** The token of the Setup Link that Board Keeper wrote to its log, for creating the first Admin. */
export async function setupToken() {
  const log = await compose("logs", "app")
  const token = [...log.matchAll(/\/setup\?token=([\w-]+)/g)].at(-1)?.[1]
  if (!token) {
    throw new Error("Board Keeper's log has no Setup Link.")
  }
  return token
}

/**
 * Keeps a copy of the database as it is now, for every test to start from. PostgreSQL copies a
 * database only while nobody is connected to it, so the application is stopped meanwhile.
 */
export async function keepCopy() {
  await compose("stop", "app")
  await sql(`DROP DATABASE IF EXISTS ${copy}`, `CREATE DATABASE ${copy} TEMPLATE ${database}`)
  await compose("start", "app")
  await untilUp()
}

/**
 * Puts the copy back, in about a second, while the application keeps running. Its connections to
 * the dropped database are cut, and its connection pool, HikariCP, replaces them, but only once
 * it checks them: it doesn't check a connection that was used in the last half second
 * ("aliveBypassWindowMs"). So the next test waits until every connection is older than that.
 */
export async function restoreCopy() {
  await sql(
    `DROP DATABASE ${database} WITH (FORCE)`,
    `CREATE DATABASE ${database} TEMPLATE ${copy}`,
  )
  await new Promise((resolve) => setTimeout(resolve, 600))
  await untilUp()
}
