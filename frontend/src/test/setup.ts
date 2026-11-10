// Adds matchers such as toBeInTheDocument() to expect().
import "@testing-library/jest-dom/vitest"
import { cleanup } from "@testing-library/react"
import { afterAll, afterEach, beforeEach } from "vitest"
import { queryClient } from "@/api/queryClient"
import i18n from "@/i18n"
import { server } from "./server"

// Start catching requests now, before any test file is loaded: the API client keeps hold of
// `fetch` as it is at the moment the client is created. A request that no test has set up an
// answer for fails the test, instead of going unnoticed.
server.listen({ onUnhandledFrame: "error" })

// In tests a failed request should show its error at once, not after three more attempts.
queryClient.setDefaultOptions({ queries: { retry: false } })

// Every test starts in English, with nothing remembered from the test before.
beforeEach(async () => {
  localStorage.clear()
  document.cookie = "XSRF-TOKEN=; max-age=0"
  await i18n.changeLanguage("en")
})

afterEach(() => {
  cleanup()
  queryClient.clear()
  server.resetHandlers()
})

afterAll(() => server.close())
