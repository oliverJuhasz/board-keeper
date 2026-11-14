import type { Query } from "@tanstack/react-query"
import { expect, test } from "vitest"
import { shouldKeep } from "./persistence"

/** A query of openapi-react-query that has answered. */
function answered(path: string, data: unknown) {
  return { queryKey: ["get", path, {}], state: { status: "success", data } } as unknown as Query
}

test("keeps the answers the start page and the form need", () => {
  expect(shouldKeep(answered("/api/games", []))).toBe(true)
  expect(shouldKeep(answered("/api/stats/games/{gameId}", {}))).toBe(false)
})

test("keeps who is signed in, but never that nobody is", () => {
  expect(shouldKeep(answered("/api/session", { account: { id: "a" }, passkeys: true }))).toBe(true)
  // Saved at most once a second, a "signed out" from before signing in could otherwise send a
  // page loaded right afterwards back to the sign-in page.
  expect(shouldKeep(answered("/api/session", { passkeys: true }))).toBe(false)
})
