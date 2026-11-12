import createFetchClient, { type Middleware } from "openapi-fetch"
import createQueryHooks from "openapi-react-query"
import { queryClient } from "./queryClient"
import type { components, paths } from "./schema"

// schema.d.ts is generated from api/openapi.json ("npm run generate:api"), which the backend
// build keeps in step with the real API. A call to an address that doesn't exist, or a use of
// a field the API doesn't return, is therefore a compile error.
export const fetchClient = createFetchClient<paths>({ baseUrl: window.location.origin })

/** The API as TanStack Query hooks, for example `api.useQuery("get", "/api/games")`. */
export const api = createQueryHooks(fetchClient)

/**
 * The cache key of "who is signed in", for updating it after signing in or out. The `{}` has
 * to match what useSession passes, because the options are part of the key.
 */
export const sessionKey = api.queryOptions("get", "/api/session", {}).queryKey

function cookie(name: string) {
  return document.cookie
    .split("; ")
    .find((entry) => entry.startsWith(`${name}=`))
    ?.slice(name.length + 1)
}

// Protection against forged requests: the server hands out a token in a cookie, and every
// request that changes something has to send it back in a header. Another website can make
// the browser send our cookies along, but it can't read them, so it can't fill in the header.
const csrfToken: Middleware = {
  onRequest({ request }) {
    const token = cookie("XSRF-TOKEN")
    if (token && !["GET", "HEAD", "OPTIONS"].includes(request.method)) {
      request.headers.set("X-XSRF-TOKEN", decodeURIComponent(token))
    }
    return request
  },
}

// When the server says we aren't signed in after all (the session ran out, or the Account was
// disabled), ask again who is signed in. The answer sends the app to the sign-in page.
const signedOutElsewhere: Middleware = {
  onResponse({ response, request }) {
    if (response.status === 401 && !new URL(request.url).pathname.startsWith("/api/session")) {
      void queryClient.invalidateQueries({ queryKey: sessionKey })
    }
    return response
  },
}

fetchClient.use(csrfToken, signedOutElsewhere)

export type Game = components["schemas"]["Game"]
export type GameModule = components["schemas"]["GameModule"]
export type Session = components["schemas"]["Session"]
export type CurrentAccount = components["schemas"]["CurrentAccount"]
export type Permission = CurrentAccount["permissions"][number]
export type Problem = components["schemas"]["Problem"]
export type Player = components["schemas"]["Player"]
export type Invite = components["schemas"]["Invite"]
export type Role = components["schemas"]["Role"]
export type Account = components["schemas"]["Account"]
export type Play = components["schemas"]["Play"]
export type NewPlay = components["schemas"]["NewPlay"]
export type Result = components["schemas"]["Result"]
