import { QueryClient } from "@tanstack/react-query"

/** TanStack Query's cache of data loaded from the server, shared by the whole app. */
export const queryClient = new QueryClient()
