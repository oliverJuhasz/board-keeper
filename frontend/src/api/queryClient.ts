import { QueryClient } from "@tanstack/react-query"

/** Whether the server answered and refused (a 4xx status), as opposed to failing or not answering. */
function wasRefused(error: unknown) {
  const status = (error as { status?: unknown } | null)?.status
  return typeof status === "number" && status >= 400 && status < 500
}

/** TanStack Query's cache of data loaded from the server, shared by the whole app. */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // A failed load is tried twice more, in case the connection only hiccuped. A refusal,
      // such as "not found" or "not allowed", would be the same every time, so it is shown at once.
      retry: (failures, error) => failures < 2 && !wasRefused(error),
    },
  },
})
