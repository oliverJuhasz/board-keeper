import { remove, start } from "./installation.ts"

/**
 * Runs once before all tests (`globalSetup` in playwright.config.ts): starts an Installation
 * with an empty database. One left behind by an interrupted run is removed first, so the Setup
 * Link is offered again.
 */
export default async function globalSetup() {
  await remove()
  await start()
}
