import { QueryClientProvider } from "@tanstack/react-query"
import { RouterProvider } from "react-router/dom"
import { queryClient } from "./api/queryClient"
import { router } from "./router"
import { UpdateNotice } from "./routes/UpdateNotice"

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
      <UpdateNotice />
    </QueryClientProvider>
  )
}
