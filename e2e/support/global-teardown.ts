import { remove } from "./installation.ts"

/** Runs once after all tests (`globalTeardown` in playwright.config.ts): removes the Installation. */
export default async function globalTeardown() {
  await remove()
}
