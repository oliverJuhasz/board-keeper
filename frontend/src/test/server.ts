import { setupServer } from "msw/node"

/**
 * A stand-in for the backend in tests. Mock Service Worker (MSW) catches the requests the
 * app makes and answers them with whatever the test has set up through `server.use(...)`.
 */
export const server = setupServer()
