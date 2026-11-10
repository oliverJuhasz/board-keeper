import { screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { beforeEach, expect, test } from "vitest"
import { anna, gamesAre, problem, signedInAs } from "@/test/api"
import { renderApp } from "@/test/renderApp"
import { server } from "@/test/server"

const PASSWORD = "correct horse battery staple"
const NEW_PASSWORD = "a brand new long password"

beforeEach(() => signedInAs(anna))

test("switches everything to the chosen language, and saves the choice on the account", async () => {
  gamesAre([])
  let saved: unknown
  server.use(
    http.put("*/api/account/language", async ({ request }) => {
      saved = await request.json()
      return new HttpResponse(null, { status: 204 })
    }),
  )
  renderApp("/settings")

  await userEvent.selectOptions(await screen.findByRole("combobox", { name: "Language" }), "Magyar")

  expect(await screen.findByRole("heading", { name: "Beállítások" })).toBeInTheDocument()
  expect(screen.getByRole("combobox", { name: "Nyelv" })).toHaveValue("hu")
  expect(document.documentElement).toHaveAttribute("lang", "hu")
  expect(localStorage.getItem("boardkeeper.language")).toBe("hu")
  expect(saved).toEqual({ language: "hu" })

  // The rest of the app follows, including the Game Module's name, which comes from the API.
  await userEvent.click(screen.getByRole("link", { name: "Játékok" }))
  expect(await screen.findByText("Még nincsenek játékok.")).toBeInTheDocument()
  expect(await screen.findByText("Általános")).toBeInTheDocument()
})

test("changes the password and says what happened", async () => {
  let sent: unknown
  server.use(
    http.put("*/api/account/password", async ({ request }) => {
      sent = await request.json()
      return new HttpResponse(null, { status: 204 })
    }),
  )
  renderApp("/settings")

  await userEvent.type(await screen.findByLabelText("Current password"), PASSWORD)
  await userEvent.type(screen.getByLabelText("New password"), NEW_PASSWORD)
  await userEvent.click(screen.getByRole("button", { name: "Change password" }))

  expect(await screen.findByText(/Your password has been changed/)).toBeInTheDocument()
  expect(sent).toEqual({ currentPassword: PASSWORD, newPassword: NEW_PASSWORD })
  expect(screen.getByLabelText("Current password")).toHaveValue("")
})

test("says so when the current password is wrong", async () => {
  server.use(
    http.put("*/api/account/password", () => problem(403, "accounts.wrongCurrentPassword")),
  )
  renderApp("/settings")

  await userEvent.type(await screen.findByLabelText("Current password"), "not my password")
  await userEvent.type(screen.getByLabelText("New password"), NEW_PASSWORD)
  await userEvent.click(screen.getByRole("button", { name: "Change password" }))

  expect(await screen.findByText("The current password is wrong.")).toBeInTheDocument()
})

test("refuses a new password shorter than 16 characters before sending anything", async () => {
  renderApp("/settings")

  await userEvent.type(await screen.findByLabelText("Current password"), PASSWORD)
  await userEvent.type(screen.getByLabelText("New password"), "fifteen chars..")
  await userEvent.click(screen.getByRole("button", { name: "Change password" }))

  expect(await screen.findByText("Use at least 16 characters.")).toBeInTheDocument()
})
