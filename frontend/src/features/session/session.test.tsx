import { waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { expect, test } from "vitest"
import { anna, bence, gamesAre, problem, signedInAs, signedOut } from "@/test/api"
import { page } from "@/test/queries"
import { renderApp } from "@/test/renderApp"
import { server } from "@/test/server"

const PASSWORD = "correct horse battery staple"

test("sends a visitor who isn't signed in to the sign-in page", async () => {
  signedOut()

  renderApp("/")

  expect(await page.findByTestIdWith("page-title", "Sign in")).toBeInTheDocument()
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

  await userEvent.type(await page.findByTestId("username"), "anna")
  await userEvent.type(page.getByTestId("password"), PASSWORD)
  await userEvent.click(page.getByTestId("sign-in"))

  expect(await page.findByTestId("no-games")).toBeInTheDocument()
  expect(sent).toEqual({
    body: { username: "anna", password: PASSWORD, staySignedIn: true },
    csrfHeader: "token-from-the-server",
  })
})

test("says so when the username or password is wrong", async () => {
  signedOut()
  server.use(http.post("*/api/session", () => problem(401, "session.badCredentials")))
  renderApp("/sign-in")

  await userEvent.type(await page.findByTestId("username"), "anna")
  await userEvent.type(page.getByTestId("password"), "not my password")
  await userEvent.click(page.getByTestId("sign-in"))

  expect(await page.findByTestId("problem")).toHaveTextContent("Wrong username or password.")
})

test("asks for a username and password before sending anything", async () => {
  signedOut()
  renderApp("/sign-in")

  // No answer is set up for POST /api/session, so sending the form would fail this test.
  await userEvent.click(await page.findByTestId("sign-in"))

  expect(await page.findAllByTestIdWith("field-error", "Fill this in.")).toHaveLength(2)
})

test("explains what to do when the installation has no accounts yet", async () => {
  signedOut()
  server.use(http.get("*/api/setup", () => HttpResponse.json({ required: true })))

  renderApp("/sign-in")

  expect(
    await page.findByTestIdWith("page-title", "Board Keeper isn't set up yet"),
  ).toBeInTheDocument()
  expect(page.queryByTestId("username")).not.toBeInTheDocument()
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

  await userEvent.type(await page.findByTestId("player-name"), "Anna")
  await userEvent.type(page.getByTestId("username"), "anna")
  await userEvent.type(page.getByTestId("password"), PASSWORD)
  await userEvent.click(page.getByTestId("set-up"))

  expect(await page.findByTestId("no-games")).toBeInTheDocument()
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

  await userEvent.type(await page.findByTestId("player-name"), "Anna")
  await userEvent.type(page.getByTestId("username"), "anna")
  await userEvent.type(page.getByTestId("password"), "fifteen chars..")
  await userEvent.click(page.getByTestId("set-up"))

  expect(
    await page.findByTestIdWith("field-error", "Use at least 16 characters."),
  ).toBeInTheDocument()
})

test("the setup page says so when the link has no token", async () => {
  server.use(
    http.get("*/api/session", () => HttpResponse.json({})),
    http.get("*/api/setup", () => HttpResponse.json({ required: true })),
  )

  renderApp("/setup")

  expect(await page.findByTestIdWith("page-title", "This link is incomplete")).toBeInTheDocument()
})

test("the setup page points to signing in once setup is done", async () => {
  signedOut()

  renderApp("/setup?token=from-the-log")

  expect(
    await page.findByTestIdWith("page-title", "Board Keeper is already set up"),
  ).toBeInTheDocument()
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

  await userEvent.click(await page.findByTestId("account-menu"))
  expect(await page.findByTestId("signed-in-as")).toHaveTextContent("Signed in as anna")
  await userEvent.click(page.getByTestId("sign-out"))

  expect(await page.findByTestIdWith("page-title", "Sign in")).toBeInTheDocument()
  expect(signedOutOnServer).toBe(true)
})

test("goes to the sign-in page when the server says the session has ended", async () => {
  signedInAs(anna)
  gamesAre([])
  renderApp("/")
  await page.findByTestId("no-games")

  // From now on the server no longer knows us, as after a session has run out.
  signedOut()
  server.use(http.get("*/api/games", () => new HttpResponse(null, { status: 401 })))
  const { queryClient } = await import("@/api/queryClient")
  await queryClient.invalidateQueries({ queryKey: ["get", "/api/games"] })

  await waitFor(() => expect(page.getByTestId("page-title")).toHaveTextContent("Sign in"))
})

test("the account menu leads an admin to settings, accounts and roles", async () => {
  signedInAs(anna)
  gamesAre([])
  renderApp("/")

  await userEvent.click(await page.findByTestId("account-menu"))

  expect(await page.findByTestId("settings")).toHaveAttribute("href", "/settings")
  expect(page.getByTestId("accounts")).toHaveAttribute("href", "/accounts")
  expect(page.getByTestId("roles")).toHaveAttribute("href", "/roles")
})

test("the account menu shows a member only what a member can use", async () => {
  signedInAs(bence)
  gamesAre([])
  renderApp("/")

  await userEvent.click(await page.findByTestId("account-menu"))

  expect(await page.findByTestId("settings")).toBeInTheDocument()
  expect(page.queryByTestId("accounts")).not.toBeInTheDocument()
  expect(page.queryByTestId("roles")).not.toBeInTheDocument()
})

test("the account menu leads to the page about Board Keeper", async () => {
  signedInAs(bence)
  gamesAre([])
  renderApp("/")

  await userEvent.click(await page.findByTestId("account-menu"))
  await userEvent.click(await page.findByTestId("about"))

  expect(await page.findByTestIdWith("page-title", "About Board Keeper")).toBeInTheDocument()
})
