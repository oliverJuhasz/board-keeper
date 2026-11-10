import { QueryClientProvider } from "@tanstack/react-query"
import { render } from "@testing-library/react"
import { createMemoryRouter } from "react-router"
import { RouterProvider } from "react-router/dom"
import { queryClient } from "@/api/queryClient"
import { routes } from "@/router"

/** Draws the whole app as if the browser were at the given address. */
export function renderApp(path = "/") {
  const router = createMemoryRouter(routes, { initialEntries: [path] })

  return render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
}
