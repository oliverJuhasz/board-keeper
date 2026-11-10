// Adds matchers such as toBeInTheDocument() to expect().
import "@testing-library/jest-dom/vitest"
import { cleanup } from "@testing-library/react"
import { afterAll, afterEach } from "vitest"
import { server } from "./server"

// Start catching requests now, before any test file is loaded: the API client keeps hold of
// `fetch` as it is at the moment the client is created. A request that no test has set up an
// answer for fails the test, instead of going unnoticed.
server.listen({ onUnhandledFrame: "error" })

afterEach(() => {
  cleanup()
  server.resetHandlers()
})

afterAll(() => server.close())
