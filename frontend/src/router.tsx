import { createBrowserRouter, type RouteObject } from "react-router"
import { AppLayout } from "./routes/AppLayout"
import { HomePage } from "./routes/HomePage"
import { NotFoundPage } from "./routes/NotFoundPage"

// Which page is shown for which address. Every page is drawn inside AppLayout.
export const routes: RouteObject[] = [
  {
    path: "/",
    element: <AppLayout />,
    children: [
      { index: true, element: <HomePage /> },
      { path: "*", element: <NotFoundPage /> },
    ],
  },
]

export const router = createBrowserRouter(routes)
