import { screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { expect, test } from "vitest"
import { anna, bence, gamesAre, problem, signedInAs, signedOut } from "@/test/api"
import { renderApp } from "@/test/renderApp"
import { server } from "@/test/server"

const PASSWORD = "correct horse battery staple"

test("sends a visitor who isn't signed in to the sign-in page", async () => {
  signedOut()

  renderApp("/")

  expect(await screen.findByRole("heading", { name: "Sign in" })).toBeInTheDocument()
})

test("signs in and goes on to the page that was asked for", async () => {
  signedOut()
  gamesAre([])
  document.cookie = "XSRF-TOKEN=token-from-the-server"
  let sent: { body: unknown; csrfHeader: string | null } | undefined
  server.use(
    http.post("*/api/session", async ({ request }) => {
      sent = { body: await request.json(), csrfHeader: request.headers.get("X-XSRF-TOKEN") }
      signedInAs(anna)
      return HttpResponse.json({ account: anna })
    }),
  )
  renderApp("/")

  await userEvent.type(await screen.findByLabelText("Username"), "anna")
  await userEvent.type(screen.getByLabelText("Password"), PASSWORD)
  await userEvent.click(screen.getByRole("button", { name: "Sign in" }))

  expect(await screen.findByText("No games yet.")).toBeInTheDocument()
  expect(sent).toEqual({
    body: { username: "anna", password: PASSWORD, staySignedIn: true },
    csrfHeader: "token-from-the-server",
  })
})

test("says so when the username or password is wrong", async () => {
  signedOut()
  server.use(http.post("*/api/session", () => problem(401, "session.badCredentials")))
  renderApp("/sign-in")

  await userEvent.type(await screen.findByLabelText("Username"), "anna")
  await userEvent.type(screen.getByLabelText("Password"), "not my password")
  await userEvent.click(screen.getByRole("button", { name: "Sign in" }))

  expect(await screen.findByText("Wrong username or password.")).toBeInTheDocument()
})

test("asks for a username and password before sending anything", async () => {
  signedOut()
  renderApp("/sign-in")

  // No answer is set up for POST /api/session, so sending the form would fail this test.
  await userEvent.click(await screen.findByRole("button", { name: "Sign in" }))

  expect(await screen.findAllByText("Fill this in.")).toHaveLength(2)
})

test("explains what to do when the installation has no accounts yet", async () => {
  signedOut()
  server.use(http.get("*/api/setup", () => HttpResponse.json({ required: true })))

  renderApp("/sign-in")

  expect(await screen.findByText("Board Keeper isn't set up yet")).toBeInTheDocument()
  expect(screen.queryByLabelText("Username")).not.toBeInTheDocument()
})

test("the setup link creates the first admin and signs in", async () => {
  server.use(
    http.get("*/api/session", () => HttpResponse.json({})),
    http.get("*/api/setup", () => HttpResponse.json({ required: true })),
  )
  gamesAre([])
  let sent: unknown
  server.use(
    http.post("*/api/setup", async ({ request }) => {
      sent = await request.json()
      signedInAs(anna)
      return HttpResponse.json({ account: anna })
    }),
  )
  renderApp("/setup?token=from-the-log")

  await userEvent.type(await screen.findByLabelText("Your name"), "Anna")
  await userEvent.type(screen.getByLabelText("Username"), "anna")
  await userEvent.type(screen.getByLabelText("Password"), PASSWORD)
  await userEvent.click(screen.getByRole("button", { name: "Create account" }))

  expect(await screen.findByText("No games yet.")).toBeInTheDocument()
  expect(sent).toEqual({
    token: "from-the-log",
    playerName: "Anna",
    username: "anna",
    password: PASSWORD,
    language: "en",
  })
})

test("the setup form refuses a password shorter than 16 characters", async () => {
  server.use(
    http.get("*/api/session", () => HttpResponse.json({})),
    http.get("*/api/setup", () => HttpResponse.json({ required: true })),
  )
  renderApp("/setup?token=from-the-log")

  await userEvent.type(await screen.findByLabelText("Your name"), "Anna")
  await userEvent.type(screen.getByLabelText("Username"), "anna")
  await userEvent.type(screen.getByLabelText("Password"), "fifteen chars..")
  await userEvent.click(screen.getByRole("button", { name: "Create account" }))

  expect(await screen.findByText("Use at least 16 characters.")).toBeInTheDocument()
})

test("the setup page says so when the link has no token", async () => {
  server.use(
    http.get("*/api/session", () => HttpResponse.json({})),
    http.get("*/api/setup", () => HttpResponse.json({ required: true })),
  )

  renderApp("/setup")

  expect(await screen.findByText("This link is incomplete")).toBeInTheDocument()
})

test("the setup page points to signing in once setup is done", async () => {
  signedOut()

  renderApp("/setup?token=from-the-log")

  expect(await screen.findByText("Board Keeper is already set up")).toBeInTheDocument()
})

test("signs out from the account menu", async () => {
  signedInAs(anna)
  gamesAre([])
  let signedOutOnServer = false
  server.use(
    http.delete("*/api/session", () => {
      signedOutOnServer = true
      return new HttpResponse(null, { status: 204 })
    }),
    http.get("*/api/setup", () => HttpResponse.json({ required: false })),
  )
  renderApp("/")

  await userEvent.click(await screen.findByRole("button", { name: "Account" }))
  expect(await screen.findByText("Signed in as anna")).toBeInTheDocument()
  await userEvent.click(screen.getByRole("menuitem", { name: "Sign out" }))

  expect(await screen.findByRole("heading", { name: "Sign in" })).toBeInTheDocument()
  expect(signedOutOnServer).toBe(true)
})

test("goes to the sign-in page when the server says the session has ended", async () => {
  signedInAs(anna)
  gamesAre([])
  renderApp("/")
  await screen.findByText("No games yet.")

  // From now on the server no longer knows us, as after a session has run out.
  signedOut()
  server.use(http.get("*/api/games", () => new HttpResponse(null, { status: 401 })))
  const { queryClient } = await import("@/api/queryClient")
  await queryClient.invalidateQueries({ queryKey: ["get", "/api/games"] })

  await waitFor(() => expect(screen.getByRole("heading", { name: "Sign in" })).toBeInTheDocument())
})

test("the account menu leads an admin to settings, accounts and roles", async () => {
  signedInAs(anna)
  gamesAre([])
  renderApp("/")

  await userEvent.click(await screen.findByRole("button", { name: "Account" }))

  expect(await screen.findByRole("menuitem", { name: "Settings" })).toHaveAttribute(
    "href",
    "/settings",
  )
  expect(screen.getByRole("menuitem", { name: "Accounts" })).toHaveAttribute("href", "/accounts")
  expect(screen.getByRole("menuitem", { name: "Roles" })).toHaveAttribute("href", "/roles")
})

test("the account menu shows a member only what a member can use", async () => {
  signedInAs(bence)
  gamesAre([])
  renderApp("/")

  await userEvent.click(await screen.findByRole("button", { name: "Account" }))

  expect(await screen.findByRole("menuitem", { name: "Settings" })).toBeInTheDocument()
  expect(screen.queryByRole("menuitem", { name: "Accounts" })).not.toBeInTheDocument()
  expect(screen.queryByRole("menuitem", { name: "Roles" })).not.toBeInTheDocument()
})
