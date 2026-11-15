import { screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { afterEach, beforeEach, expect, test, vi } from "vitest"
import type { ApiKey } from "@/api/client"
import { anna, bence, problem, signedInAs } from "@/test/api"
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

/** The card, found by its title. A card's title is plain text, not a heading. */
async function apiKeysCard() {
  const title = await screen.findByText("API keys", { selector: "[data-slot=card-title]" })
  const card = title.closest<HTMLElement>("[data-slot=card]")
  if (!card) {
    throw new Error("The API keys card is missing")
  }
  return card
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
  expect(await within(card).findByText("Backup script")).toBeInTheDocument()
  expect(within(card).getByText(/last used .* · never expires/)).toBeInTheDocument()
  expect(within(card).getByText(/not used yet · expired /)).toBeInTheDocument()
})

test("creates an API key, confirmed with the password, and shows it once", async () => {
  const state = apiKeysAre([])
  renderApp("/settings")

  const card = await apiKeysCard()
  expect(await within(card).findByText("You have no API keys yet.")).toBeInTheDocument()
  await userEvent.click(within(card).getByRole("button", { name: "Create an API key" }))
  const dialog = await screen.findByRole("dialog")
  await userEvent.type(within(dialog).getByLabelText("Name"), "Claude")
  await userEvent.click(within(dialog).getByRole("radio", { name: "Never" }))
  await userEvent.type(within(dialog).getByLabelText("Your password"), PASSWORD)
  await userEvent.click(within(dialog).getByRole("button", { name: "Create API key" }))

  expect(await within(dialog).findByLabelText("API key")).toHaveValue(SECRET)
  expect(within(dialog).getByText(/won't be shown again/)).toBeInTheDocument()
  expect(state.sent).toEqual([{ name: "Claude", expiry: "NEVER", password: PASSWORD }])

  await userEvent.click(within(dialog).getByRole("button", { name: "Done" }))
  await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument())
  expect(await within(card).findByText("Claude")).toBeInTheDocument()
  expect(screen.queryByText(SECRET)).not.toBeInTheDocument()
})

test("expires in 90 days unless another expiry is chosen", async () => {
  const state = apiKeysAre([])
  renderApp("/settings")

  await userEvent.click(
    within(await apiKeysCard()).getByRole("button", { name: "Create an API key" }),
  )
  const dialog = await screen.findByRole("dialog")
  expect(within(dialog).getByRole("radio", { name: "In 90 days" })).toBeChecked()
  await userEvent.type(within(dialog).getByLabelText("Name"), "Script")
  await userEvent.type(within(dialog).getByLabelText("Your password"), PASSWORD)
  await userEvent.click(within(dialog).getByRole("button", { name: "Create API key" }))

  await within(dialog).findByLabelText("API key")
  expect(state.sent).toEqual([{ name: "Script", expiry: "NINETY_DAYS", password: PASSWORD }])
})

test("needs a name and the password before sending anything", async () => {
  const state = apiKeysAre([])
  renderApp("/settings")

  await userEvent.click(
    within(await apiKeysCard()).getByRole("button", { name: "Create an API key" }),
  )
  const dialog = await screen.findByRole("dialog")
  await userEvent.click(within(dialog).getByRole("button", { name: "Create API key" }))

  expect(await within(dialog).findAllByText("Fill this in.")).toHaveLength(2)
  expect(state.sent).toEqual([])
})

test("says so when the password is wrong", async () => {
  apiKeysAre([])
  server.use(
    http.post("*/api/account/api-keys", () => problem(403, "accounts.wrongCurrentPassword")),
  )
  renderApp("/settings")

  await userEvent.click(
    within(await apiKeysCard()).getByRole("button", { name: "Create an API key" }),
  )
  const dialog = await screen.findByRole("dialog")
  await userEvent.type(within(dialog).getByLabelText("Name"), "Script")
  await userEvent.type(within(dialog).getByLabelText("Your password"), "not my password")
  await userEvent.click(within(dialog).getByRole("button", { name: "Create API key" }))

  expect(await within(dialog).findByText("The current password is wrong.")).toBeInTheDocument()
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

  await userEvent.click(
    within(await apiKeysCard()).getByRole("button", { name: "Create an API key" }),
  )
  const dialog = await screen.findByRole("dialog")
  await userEvent.type(within(dialog).getByLabelText("Name"), "Agent")
  await userEvent.click(
    await within(dialog).findByRole("button", { name: "Confirm with a passkey instead" }),
  )

  expect(await within(dialog).findByLabelText("API key")).toHaveValue(SECRET)
  expect(phone.get).toHaveBeenCalledWith(expect.objectContaining({ publicKey: options }))
  expect(state.sent).toEqual([{ name: "Agent", expiry: "NINETY_DAYS", passkey: assertion }])
})

test("deletes an API key after asking", async () => {
  const state = apiKeysAre([backupScript])
  renderApp("/settings")

  const card = await apiKeysCard()
  await userEvent.click(await within(card).findByRole("button", { name: "Delete Backup script" }))
  const dialog = await screen.findByRole("alertdialog")
  expect(within(dialog).getByText(/stop working at once/)).toBeInTheDocument()
  await userEvent.click(within(dialog).getByRole("button", { name: "Delete API key" }))

  expect(await within(card).findByText("You have no API keys yet.")).toBeInTheDocument()
  expect(state.apiKeys).toEqual([])
})

test("isn't shown to an account that may not use API keys and has none", async () => {
  signedInAs(bence)
  apiKeysAre([])
  renderApp("/settings")

  expect(
    await screen.findByText("Change password", { selector: "[data-slot=card-title]" }),
  ).toBeInTheDocument()
  // Give the list a moment to arrive.
  await new Promise((resolve) => setTimeout(resolve, 50))
  expect(screen.queryByText("API keys")).not.toBeInTheDocument()
})

test("without the permission, shows why the API keys don't work, and lets them be deleted", async () => {
  signedInAs(bence)
  apiKeysAre([backupScript])
  renderApp("/settings")

  const card = await apiKeysCard()
  expect(within(card).getByText(/may no longer use API keys/)).toBeInTheDocument()
  expect(within(card).getByRole("button", { name: "Delete Backup script" })).toBeInTheDocument()
  expect(within(card).queryByRole("button", { name: "Create an API key" })).not.toBeInTheDocument()
})
