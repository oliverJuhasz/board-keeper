import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { beforeEach, expect, test } from "vitest"
import { anna, gamesAre, problem, signedInAs } from "@/test/api"
import { page } from "@/test/queries"
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

  const language = await page.findByTestId("language")
  expect(language).toHaveAccessibleName("Language")
  await userEvent.selectOptions(language, "Magyar")

  expect(await page.findByTestIdWith("page-title", "Beállítások")).toBeInTheDocument()
  expect(language).toHaveAccessibleName("Nyelv")
  expect(language).toHaveValue("hu")
  expect(document.documentElement).toHaveAttribute("lang", "hu")
  expect(localStorage.getItem("boardkeeper.language")).toBe("hu")
  expect(saved).toEqual({ language: "hu" })

  // The rest of the app follows.
  expect(page.getByTestId("nav-games")).toHaveTextContent("Játékok")
  await userEvent.click(page.getByTestId("nav-games"))
  expect(await page.findByTestId("no-games")).toHaveTextContent("Még nincsenek játékok.")
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

  await userEvent.type(await page.findByTestId("current-password"), PASSWORD)
  await userEvent.type(page.getByTestId("new-password"), NEW_PASSWORD)
  await userEvent.click(page.getByTestId("change-password"))

  expect(await page.findByTestId("password-changed")).toHaveTextContent(
    /Your password has been changed/,
  )
  expect(sent).toEqual({ currentPassword: PASSWORD, newPassword: NEW_PASSWORD })
  expect(page.getByTestId("current-password")).toHaveValue("")
})

test("says so when the current password is wrong", async () => {
  server.use(
    http.put("*/api/account/password", () => problem(403, "accounts.wrongCurrentPassword")),
  )
  renderApp("/settings")

  await userEvent.type(await page.findByTestId("current-password"), "not my password")
  await userEvent.type(page.getByTestId("new-password"), NEW_PASSWORD)
  await userEvent.click(page.getByTestId("change-password"))

  expect(await page.findByTestId("problem")).toHaveTextContent("The current password is wrong.")
})

test("refuses a new password shorter than 16 characters before sending anything", async () => {
  renderApp("/settings")

  await userEvent.type(await page.findByTestId("current-password"), PASSWORD)
  await userEvent.type(page.getByTestId("new-password"), "fifteen chars..")
  await userEvent.click(page.getByTestId("change-password"))

  expect(
    await page.findByTestIdWith("field-error", "Use at least 16 characters."),
  ).toBeInTheDocument()
})
