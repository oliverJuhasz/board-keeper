import { api } from "@/api/client"

// The names under which TanStack Query keeps what it fetched about Plays. A page that changes
// a Play uses them to update or throw away what other pages have fetched.

/** Every page of the play history, whatever its filters: all their keys start like this. */
export const playsKey = ["get", "/api/plays"] as const

/** One Play. */
export function playKey(id: string) {
  return api.queryOptions("get", "/api/plays/{id}", { params: { path: { id } } }).queryKey
}
