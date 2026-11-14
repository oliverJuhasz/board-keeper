import { PersistQueryClientProvider } from "@tanstack/react-query-persist-client"
import { RouterProvider } from "react-router/dom"
import { persistOptions } from "./api/persistence"
import { queryClient } from "./api/queryClient"
import { router } from "./router"
import { UpdateNotice } from "./routes/UpdateNotice"

export function App() {
  return (
    // Like QueryClientProvider, and it also keeps part of the cache on the device (persistence.ts).
    <PersistQueryClientProvider client={queryClient} persistOptions={persistOptions}>
      <RouterProvider router={router} />
      <UpdateNotice />
    </PersistQueryClientProvider>
  )
}
