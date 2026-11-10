import { screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { expect, test } from "vitest"
import { bence, gamesAre, problem, signedInAs } from "@/test/api"
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

  expect(await screen.findByText(/You've been invited as Bence/)).toBeInTheDocument()
  await userEvent.type(screen.getByLabelText("Username"), "bence")
  await userEvent.type(screen.getByLabelText("Password"), PASSWORD)
  await userEvent.click(screen.getByRole("button", { name: "Create account" }))

  expect(await screen.findByText("No games yet.")).toBeInTheDocument()
  expect(sent).toEqual({
    token: "the-secret-token",
    body: { username: "bence", password: PASSWORD, language: "en" },
  })
})

test("says so when the username is taken", async () => {
  inviteIsFor("Bence")
  server.use(http.post("*/api/registration/:token", () => problem(409, "accounts.usernameTaken")))
  renderApp("/register/the-secret-token")

  await userEvent.type(await screen.findByLabelText("Username"), "anna")
  await userEvent.type(screen.getByLabelText("Password"), PASSWORD)
  await userEvent.click(screen.getByRole("button", { name: "Create account" }))

  expect(await screen.findByText("This username is taken.")).toBeInTheDocument()
})

test("checks the username and password before sending anything", async () => {
  inviteIsFor("Bence")
  renderApp("/register/the-secret-token")

  await userEvent.type(await screen.findByLabelText("Username"), "b!")
  await userEvent.type(screen.getByLabelText("Password"), "too short")
  await userEvent.click(screen.getByRole("button", { name: "Create account" }))

  expect(
    await screen.findByText("Use 3 to 30 letters, digits, dots, hyphens or underscores."),
  ).toBeInTheDocument()
  expect(screen.getByText("Use at least 16 characters.")).toBeInTheDocument()
})

test("explains when an invite link no longer works", async () => {
  server.use(
    http.get("*/api/session", () => HttpResponse.json({})),
    http.get("*/api/registration/:token", () => problem(404, "invites.notValid")),
  )

  renderApp("/register/used-up-token")

  expect(await screen.findByText("This invite link doesn't work")).toBeInTheDocument()
})
