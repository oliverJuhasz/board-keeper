import { QueryClientProvider } from "@tanstack/react-query"
import { render } from "@testing-library/react"
import { createMemoryRouter, type RouteObject } from "react-router"
import { RouterProvider } from "react-router/dom"
import { beforeAll } from "vitest"
import { queryClient } from "@/api/queryClient"
import { appRoutes } from "@/router"

/**
 * Loads the code of every page, which the router otherwise loads the first time a page is
 * shown. In a test, that first time includes compiling the page and all it uses, which can
 * take longer than a test waits for something to appear.
 */
async function loadEveryPage(routes: RouteObject[]): Promise<void> {
  await Promise.all(
    routes.map(async (route) => {
      if (route.lazy && typeof route.lazy === "object") {
        await Promise.all(Object.values(route.lazy).map((load) => load?.()))
      }
      await loadEveryPage(route.children ?? [])
    }),
  )
}

// Once for each test file that draws the app, before its first test.
beforeAll(() => loadEveryPage(appRoutes()), 30_000)

/** Draws the whole app as if the browser were at the given address. */
export function renderApp(path = "/") {
  const router = createMemoryRouter(appRoutes(), { initialEntries: [path] })

  return render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
}
