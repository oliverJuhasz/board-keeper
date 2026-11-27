import { waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { afterEach, beforeEach, expect, test, vi } from "vitest"
import type { ApiKey } from "@/api/client"
import { anna, bence, problem, signedInAs } from "@/test/api"
import { inside, page } from "@/test/queries"
import { renderApp } from "@/test/renderApp"
import { server } from "@/test/server"

const PASSWORD = "correct horse battery staple"
const SECRET = "bk_c2VjcmV0LW9mLXRoZS1uZXctYXBpLWtleS1mb3ItdGVzdHM"

const backupScript: ApiKey = {
  id: "0193a000-0000-7000-8000-0000000000e1",
  name: "Backup script",
  created: "2026-03-01T10:00:00Z",
  lastUsed: "2026-03-05T08:00:00Z",
}
const oldAgent: ApiKey = {
  id: "0193a000-0000-7000-8000-0000000000e2",
  name: "Old agent",
  created: "2025-01-01T10:00:00Z",
  expires: "2025-04-01T10:00:00Z",
}

/** The Account's API Keys as the backend keeps them; creating and deleting change the list. */
function apiKeysAre(apiKeys: ApiKey[]) {
  const state = { apiKeys: [...apiKeys], sent: [] as unknown[] }
  server.use(
    http.get("*/api/account/api-keys", () => HttpResponse.json(state.apiKeys)),
    http.post("*/api/account/api-keys", async ({ request }) => {
      const body = (await request.json()) as { name: string }
      state.sent.push(body)
      const apiKey = {
        id: "0193a000-0000-7000-8000-0000000000e9",
        name: body.name,
        created: "2026-03-10T10:00:00Z",
      }
      state.apiKeys = [apiKey, ...state.apiKeys]
      return HttpResponse.json({ apiKey, secret: SECRET })
    }),
    http.delete("*/api/account/api-keys/:id", ({ params }) => {
      state.apiKeys = state.apiKeys.filter((apiKey) => apiKey.id !== params.id)
      return new HttpResponse(null, { status: 204 })
    }),
  )
  return state
}

/** The card of API Keys on the settings page. */
async function apiKeysCard() {
  return inside(await page.findByTestId("api-keys"))
}

beforeEach(() => signedInAs(anna))

afterEach(() => {
  vi.unstubAllGlobals()
  Reflect.deleteProperty(navigator, "credentials")
})

test("lists the API keys with when they were made, last used, and stop working", async () => {
  apiKeysAre([backupScript, oldAgent])
  renderApp("/settings")

  const card = await apiKeysCard()
  expect(await card.findByTestIdWith("api-key", "Backup script")).toBeInTheDocument()
  expect(
    card.getByTestIdWith("api-key-details", /last used .* · never expires/),
  ).toBeInTheDocument()
  expect(card.getByTestIdWith("api-key-details", /not used yet · expired /)).toBeInTheDocument()
})

test("creates an API key, confirmed with the password, and shows it once", async () => {
  const state = apiKeysAre([])
  renderApp("/settings")

  const card = await apiKeysCard()
  expect(
    await card.findByTestIdWith("no-api-keys", "You have no API keys yet."),
  ).toBeInTheDocument()
  await userEvent.click(page.getByTestId("create-api-key"))
  const dialog = inside(await page.findByTestId("create-api-key-dialog"))
  await userEvent.type(dialog.getByTestId("api-key-name"), "Claude")
  await userEvent.click(dialog.getByTestId("api-key-expiry-NEVER"))
  await userEvent.type(dialog.getByTestId("api-key-password"), PASSWORD)
  await userEvent.click(dialog.getByTestId("save-api-key"))

  expect(await dialog.findByTestId("api-key-secret")).toHaveValue(SECRET)
  expect(dialog.getByTestId("api-key-shown-once")).toHaveTextContent(/won't be shown again/)
  expect(state.sent).toEqual([{ name: "Claude", expiry: "NEVER", password: PASSWORD }])

  await userEvent.click(dialog.getByTestId("api-key-done"))
  await waitFor(() => expect(page.queryByTestId("create-api-key-dialog")).not.toBeInTheDocument())
  expect(await page.findByTestIdWith("api-key", "Claude")).toBeInTheDocument()
  expect(page.queryByTestId("api-key-secret")).not.toBeInTheDocument()
  // The keyboard carries on from the button, not from the top of the page.
  await waitFor(() => expect(page.getByTestId("create-api-key")).toHaveFocus())
})

test("cancelling the dialog for a new API key gives focus back to its button", async () => {
  apiKeysAre([])
  renderApp("/settings")

  const create = await page.findByTestId("create-api-key")
  create.focus()
  await userEvent.keyboard("{Enter}")
  await page.findByTestId("create-api-key-dialog")
  await userEvent.keyboard("{Escape}")

  await waitFor(() => expect(create).toHaveFocus())
})

test("expires in 90 days unless another expiry is chosen", async () => {
  const state = apiKeysAre([])
  renderApp("/settings")

  await userEvent.click(await page.findByTestId("create-api-key"))
  const dialog = inside(await page.findByTestId("create-api-key-dialog"))
  expect(dialog.getByTestId("api-key-expiry-NINETY_DAYS")).toBeChecked()
  await userEvent.type(dialog.getByTestId("api-key-name"), "Script")
  await userEvent.type(dialog.getByTestId("api-key-password"), PASSWORD)
  await userEvent.click(dialog.getByTestId("save-api-key"))

  await dialog.findByTestId("api-key-secret")
  expect(state.sent).toEqual([{ name: "Script", expiry: "NINETY_DAYS", password: PASSWORD }])
})

test("needs a name and the password before sending anything", async () => {
  const state = apiKeysAre([])
  renderApp("/settings")

  await userEvent.click(await page.findByTestId("create-api-key"))
  const dialog = inside(await page.findByTestId("create-api-key-dialog"))
  await userEvent.click(dialog.getByTestId("save-api-key"))

  expect(await dialog.findAllByTestIdWith("field-error", "Fill this in.")).toHaveLength(2)
  expect(state.sent).toEqual([])
})

test("says so when the password is wrong", async () => {
  apiKeysAre([])
  server.use(
    http.post("*/api/account/api-keys", () => problem(403, "accounts.wrongCurrentPassword")),
  )
  renderApp("/settings")

  await userEvent.click(await page.findByTestId("create-api-key"))
  const dialog = inside(await page.findByTestId("create-api-key-dialog"))
  await userEvent.type(dialog.getByTestId("api-key-name"), "Script")
  await userEvent.type(dialog.getByTestId("api-key-password"), "not my password")
  await userEvent.click(dialog.getByTestId("save-api-key"))

  expect(
    await dialog.findByTestIdWith("problem", "The current password is wrong."),
  ).toBeInTheDocument()
})

test("confirms with a passkey instead of the password", async () => {
  const assertion = { id: "cGFzc2tleQ", type: "public-key", response: { signature: "c2ln" } }
  vi.stubGlobal(
    "PublicKeyCredential",
    class {
      static parseRequestOptionsFromJSON = (options: unknown) => options
      static parseCreationOptionsFromJSON = (options: unknown) => options
      static isConditionalMediationAvailable = () => Promise.resolve(false)
    },
  )
  const phone = { get: vi.fn(() => Promise.resolve({ toJSON: () => assertion })) }
  Object.defineProperty(navigator, "credentials", { value: phone, configurable: true })
  const options = { challenge: "Y29uZmlybQ", rpId: "localhost" }
  server.use(
    http.get("*/api/session", () => HttpResponse.json({ account: anna, passkeys: true })),
    http.get("*/api/account/passkeys", () =>
      HttpResponse.json([{ id: "cGFzc2tleQ", label: "My phone" }]),
    ),
    http.post("*/api/account/api-keys/passkey-options", () => HttpResponse.json(options)),
  )
  const state = apiKeysAre([])
  renderApp("/settings")

  await userEvent.click(await page.findByTestId("create-api-key"))
  const dialog = inside(await page.findByTestId("create-api-key-dialog"))
  await userEvent.type(dialog.getByTestId("api-key-name"), "Agent")
  await userEvent.click(await dialog.findByTestId("confirm-with-passkey"))

  expect(await dialog.findByTestId("api-key-secret")).toHaveValue(SECRET)
  expect(phone.get).toHaveBeenCalledWith(expect.objectContaining({ publicKey: options }))
  expect(state.sent).toEqual([{ name: "Agent", expiry: "NINETY_DAYS", passkey: assertion }])
})

test("deletes an API key after asking", async () => {
  const state = apiKeysAre([backupScript])
  renderApp("/settings")

  const card = await apiKeysCard()
  const remove = await card.findByTestId("delete-api-key")
  expect(remove).toHaveAccessibleName("Delete Backup script")
  await userEvent.click(remove)
  expect(await page.findByTestId("confirm-dialog")).toHaveTextContent(/stop working at once/)
  await userEvent.click(page.getByTestId("confirm"))

  expect(
    await card.findByTestIdWith("no-api-keys", "You have no API keys yet."),
  ).toBeInTheDocument()
  expect(state.apiKeys).toEqual([])
})

test("isn't shown to an account that may not use API keys and has none", async () => {
  signedInAs(bence)
  apiKeysAre([])
  renderApp("/settings")

  expect(await page.findByTestId("current-password")).toBeInTheDocument()
  // Give the list a moment to arrive.
  await new Promise((resolve) => setTimeout(resolve, 50))
  expect(page.queryByTestId("api-keys")).not.toBeInTheDocument()
})

test("without the permission, shows why the API keys don't work, and lets them be deleted", async () => {
  signedInAs(bence)
  apiKeysAre([backupScript])
  renderApp("/settings")

  const card = await apiKeysCard()
  expect(card.getByTestId("api-keys-not-allowed")).toHaveTextContent(/may no longer use API keys/)
  expect(card.getByTestId("delete-api-key")).toBeInTheDocument()
  expect(page.queryByTestId("create-api-key")).not.toBeInTheDocument()
})
