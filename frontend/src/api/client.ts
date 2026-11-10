import createFetchClient from "openapi-fetch"
import createQueryHooks from "openapi-react-query"
import type { components, paths } from "./schema"

// schema.d.ts is generated from api/openapi.json ("npm run generate:api"), which the backend
// build keeps in step with the real API. A call to an address that doesn't exist, or a use of
// a field the API doesn't return, is therefore a compile error.
const fetchClient = createFetchClient<paths>({ baseUrl: window.location.origin })

/** The API as TanStack Query hooks, for example `api.useQuery("get", "/api/games")`. */
export const api = createQueryHooks(fetchClient)

export type Game = components["schemas"]["Game"]
export type GameModule = components["schemas"]["GameModule"]
