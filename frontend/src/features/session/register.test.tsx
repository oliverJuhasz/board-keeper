import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { expect, test } from "vitest"
import { bence, gamesAre, problem, signedInAs } from "@/test/api"
import { page } from "@/test/queries"
import { renderApp } from "@/test/renderApp"
import { server } from "@/test/server"

const PASSWORD = "correct horse battery staple"

function inviteIsFor(playerName: string) {
  server.use(
    http.get("*/api/session", () => HttpResponse.json({})),
    http.get("*/api/registration/:token", () =>
      HttpResponse.json({ playerName, expiresAt: "2026-11-17T12:00:00Z" }),
    ),
  )
}

test("an invite link lets the invited person create their account", async () => {
  inviteIsFor("Bence")
  gamesAre([])
  let sent: { token: unknown; body: unknown } | undefined
  server.use(
    http.post("*/api/registration/:token", async ({ request, params }) => {
      sent = { token: params.token, body: await request.json() }
      signedInAs(bence)
      return HttpResponse.json({ account: bence })
    }),
  )
  renderApp("/register/the-secret-token")

  expect(await page.findByTestId("page-description")).toHaveTextContent(
    /You've been invited as Bence/,
  )
  await userEvent.type(page.getByTestId("username"), "bence")
  await userEvent.type(page.getByTestId("password"), PASSWORD)
  await userEvent.click(page.getByTestId("register"))

  expect(await page.findByTestId("no-games")).toBeInTheDocument()
  expect(sent).toEqual({
    token: "the-secret-token",
    body: { username: "bence", password: PASSWORD, language: "en" },
  })
})

test("says so when the username is taken", async () => {
  inviteIsFor("Bence")
  server.use(http.post("*/api/registration/:token", () => problem(409, "accounts.usernameTaken")))
  renderApp("/register/the-secret-token")

  await userEvent.type(await page.findByTestId("username"), "anna")
  await userEvent.type(page.getByTestId("password"), PASSWORD)
  await userEvent.click(page.getByTestId("register"))

  expect(await page.findByTestId("problem")).toHaveTextContent("This username is taken.")
})

test("checks the username and password before sending anything", async () => {
  inviteIsFor("Bence")
  renderApp("/register/the-secret-token")

  await userEvent.type(await page.findByTestId("username"), "b!")
  await userEvent.type(page.getByTestId("password"), "too short")
  await userEvent.click(page.getByTestId("register"))

  expect(
    await page.findByTestIdWith(
      "field-error",
      "Use 3 to 30 letters, digits, dots, hyphens or underscores.",
    ),
  ).toBeInTheDocument()
  expect(page.getByTestIdWith("field-error", "Use at least 16 characters.")).toBeInTheDocument()
})

test("explains when an invite link no longer works", async () => {
  server.use(
    http.get("*/api/session", () => HttpResponse.json({})),
    http.get("*/api/registration/:token", () => problem(404, "invites.notValid")),
  )

  renderApp("/register/used-up-token")

  expect(
    await page.findByTestIdWith("page-title", "This invite link doesn't work"),
  ).toBeInTheDocument()
})

test("a reset link lets someone choose a new password and signs them in", async () => {
  server.use(
    http.get("*/api/session", () => HttpResponse.json({})),
    http.get("*/api/password-reset/:token", () => HttpResponse.json({ username: "bence" })),
  )
  gamesAre([])
  let sent: { token: unknown; body: unknown } | undefined
  server.use(
    http.post("*/api/password-reset/:token", async ({ request, params }) => {
      sent = { token: params.token, body: await request.json() }
      signedInAs(bence)
      return HttpResponse.json({ account: bence })
    }),
  )
  renderApp("/reset-password/the-reset-token")

  expect(await page.findByTestId("page-description")).toHaveTextContent("For the account bence.")
  await userEvent.type(page.getByTestId("password"), PASSWORD)
  await userEvent.click(page.getByTestId("save-password"))

  expect(await page.findByTestId("no-games")).toBeInTheDocument()
  expect(sent).toEqual({ token: "the-reset-token", body: { password: PASSWORD } })
})

test("explains when a reset link no longer works", async () => {
  server.use(
    http.get("*/api/session", () => HttpResponse.json({})),
    http.get("*/api/password-reset/:token", () => problem(404, "resetLinks.notValid")),
  )

  renderApp("/reset-password/used-up-token")

  expect(
    await page.findByTestIdWith("page-title", "This reset link doesn't work"),
  ).toBeInTheDocument()
})
