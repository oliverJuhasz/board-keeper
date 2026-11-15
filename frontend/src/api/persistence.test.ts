import { dehydrate, QueryClient, type Query } from "@tanstack/react-query"
import { persistQueryClientRestore } from "@tanstack/react-query-persist-client"
import { expect, test } from "vitest"
import { persistOptions, shouldKeep } from "./persistence"

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

const MODULES = ["get", "/api/game-modules", {}]

/** What a version of the app left on the device: the Game Modules as it knew them. */
function keptBy(version: string) {
  const queryClient = new QueryClient()
  queryClient.setQueryData(MODULES, [{ key: "calico" }])
  localStorage.setItem(
    "boardkeeper.cache",
    JSON.stringify({ buster: version, timestamp: Date.now(), clientState: dehydrate(queryClient) }),
  )
}

/** The Game Modules as the app finds them when it starts. */
async function modulesAtStart() {
  const queryClient = new QueryClient()
  await persistQueryClientRestore({ queryClient, ...persistOptions })
  return queryClient.getQueryData(MODULES)
}

test("reads back what this version of the app kept", async () => {
  keptBy(__APP_VERSION__)

  expect(await modulesAtStart()).toEqual([{ key: "calico" }])
})

test("throws away what another version kept, which can have another shape", async () => {
  // Before versions were told apart, every version kept its data as "1".
  keptBy("1")

  expect(await modulesAtStart()).toBeUndefined()
  expect(localStorage.getItem("boardkeeper.cache")).toBeNull()
})
