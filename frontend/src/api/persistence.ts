import { createAsyncStoragePersister } from "@tanstack/query-async-storage-persister"
import type { Query } from "@tanstack/react-query"
import type { PersistQueryClientOptions } from "@tanstack/react-query-persist-client"

/**
 * What the app keeps on the device from the data it loads, so that the start page and the form
 * for a Play work without a connection: who is signed in, the Games, the Players, the Game
 * Modules with their texts, and the recent Plays. Everything else is loaded when it is needed.
 */
const KEPT = new Set([
  "/api/session",
  "/api/games",
  "/api/players",
  "/api/game-modules",
  "/api/game-modules/texts/{language}",
  "/api/plays/last-played",
  "/api/plays",
])

/** How long kept data is good for. Anything older is thrown away when the app starts. */
const KEPT_FOR = 30 * 24 * 60 * 60 * 1000

/**
 * TanStack Query's cache, saved in the browser's localStorage after every change (at most once
 * a second) and read back when the app starts. A query of openapi-react-query has the API's
 * address second in its key, as in ["get", "/api/games", {...}].
 */
export const persistOptions: Omit<PersistQueryClientOptions, "queryClient"> = {
  persister: createAsyncStoragePersister({
    storage: window.localStorage,
    key: "boardkeeper.cache",
  }),
  maxAge: KEPT_FOR,
  // Changing this throws away what every device kept, for when the API's answers change shape.
  buster: "1",
  dehydrateOptions: {
    shouldDehydrateQuery: (query: Query) =>
      query.state.status === "success" && KEPT.has(query.queryKey[1] as string),
  },
}
