import { screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { delay, http, HttpResponse } from "msw"
import { afterEach, beforeEach, expect, test, vi } from "vitest"
import { anna, gamesAre, problem } from "@/test/api"
import { renderApp } from "@/test/renderApp"
import { server } from "@/test/server"

// The browser's side of passkeys is not in jsdom. These stand-ins take its place: the options
// pass through unchanged, and the "phone" answers with a credential whose toJSON() is fixed.

const signInOptions = { challenge: "c2lnbi1pbg", rpId: "localhost" }
const creationOptions = { challenge: "YWRk", rp: { id: "localhost", name: "Board Keeper" } }
const assertion = { id: "cGFzc2tleQ", type: "public-key", response: { signature: "c2ln" } }
const attestation = { id: "bmV3", type: "public-key", response: { attestationObject: "YXR0" } }

type Phone = {
  get: ReturnType<typeof vi.fn>
  create: ReturnType<typeof vi.fn>
}

/** A browser that has passkeys, and a phone that answers with the credentials above. */
function browserWithPasskeys({ suggestions = false } = {}): Phone {
  // A class with static methods, as in a browser.
  vi.stubGlobal(
    "PublicKeyCredential",
    class {
      static parseRequestOptionsFromJSON = (options: unknown) => options
      static parseCreationOptionsFromJSON = (options: unknown) => options
      static isConditionalMediationAvailable = () => Promise.resolve(suggestions)
    },
  )
  const phone = {
    get: vi.fn(() => Promise.resolve({ toJSON: () => assertion })),
    create: vi.fn(() => Promise.resolve({ toJSON: () => attestation })),
  }
  Object.defineProperty(navigator, "credentials", { value: phone, configurable: true })
  return phone
}

/** Nobody is signed in, and this installation offers passkeys. */
function signedOutWithPasskeys() {
  server.use(
    http.get("*/api/session", () => HttpResponse.json({ passkeys: true })),
    http.get("*/api/setup", () => HttpResponse.json({ required: false })),
    http.post("*/api/session/passkey/options", () => HttpResponse.json(signInOptions)),
  )
}

function signedInWithPasskeys(passkeys: unknown[]) {
  const state = { passkeys: [...passkeys] }
  server.use(
    http.get("*/api/session", () => HttpResponse.json({ account: anna, passkeys: true })),
    http.get("*/api/account/passkeys", () => HttpResponse.json(state.passkeys)),
  )
  return state
}

/** Answers a passkey sign-in, and records what was sent. */
function passkeySignInWorks() {
  const sent: unknown[] = []
  server.use(
    http.post("*/api/session/passkey", async ({ request }) => {
      sent.push(await request.json())
      server.use(
        http.get("*/api/session", () => HttpResponse.json({ account: anna, passkeys: true })),
      )
      return HttpResponse.json({ account: anna, passkeys: true })
    }),
  )
  return sent
}

beforeEach(() => gamesAre([]))

afterEach(() => {
  vi.unstubAllGlobals()
  Reflect.deleteProperty(navigator, "credentials")
})

test("signs in with a passkey", async () => {
  const phone = browserWithPasskeys()
  signedOutWithPasskeys()
  const sent = passkeySignInWorks()
  renderApp("/sign-in")

  await userEvent.click(await screen.findByRole("checkbox", { name: "Stay signed in" }))
  await userEvent.click(screen.getByRole("button", { name: "Sign in with a passkey" }))

  expect(await screen.findByText("No games yet.")).toBeInTheDocument()
  expect(phone.get).toHaveBeenCalledWith(
    expect.objectContaining({ publicKey: signInOptions, mediation: "optional" }),
  )
  expect(sent).toEqual([{ credential: assertion, staySignedIn: false }])
})

test("offers passkeys among the username field's suggestions, where the browser can", async () => {
  const phone = browserWithPasskeys({ suggestions: true })
  signedOutWithPasskeys()
  const sent = passkeySignInWorks()
  renderApp("/sign-in")

  // The phone answers as soon as it is asked, as if the person picked the suggestion.
  expect(await screen.findByText("No games yet.")).toBeInTheDocument()
  expect(phone.get).toHaveBeenCalledWith(expect.objectContaining({ mediation: "conditional" }))
  expect(sent).toEqual([{ credential: assertion, staySignedIn: true }])
})

test("the username field invites passkey suggestions", async () => {
  browserWithPasskeys()
  signedOutWithPasskeys()
  renderApp("/sign-in")

  // The field is there before the answer to "who is signed in" says passkeys are offered.
  await waitFor(() =>
    expect(screen.getByLabelText("Username")).toHaveAttribute("autocomplete", "username webauthn"),
  )
})

test("a sign-in with the password first stops the passkey suggestions", async () => {
  // The request for passkey options starts a session of its own on the server. Its answer must
  // not arrive after the password sign-in, or it would replace the signed-in session.
  const phone = browserWithPasskeys({ suggestions: true })
  signedOutWithPasskeys()
  const optionsRequests: Request[] = []
  let sentPassword = false
  server.use(
    http.post("*/api/session/passkey/options", async ({ request }) => {
      optionsRequests.push(request)
      await delay(500)
      return HttpResponse.json(signInOptions)
    }),
    http.post("*/api/session", () => {
      // By now the request for options has been stopped.
      sentPassword = optionsRequests.every((request) => request.signal.aborted)
      server.use(
        http.get("*/api/session", () => HttpResponse.json({ account: anna, passkeys: true })),
      )
      return HttpResponse.json({ account: anna, passkeys: true })
    }),
  )
  renderApp("/sign-in")

  await waitFor(() => expect(optionsRequests).toHaveLength(1))
  await userEvent.type(screen.getByLabelText("Username"), "anna")
  await userEvent.type(screen.getByLabelText("Password"), "correct horse battery staple")
  await userEvent.click(screen.getByRole("button", { name: /^Sign in$/ }))

  expect(await screen.findByText("No games yet.")).toBeInTheDocument()
  expect(sentPassword).toBe(true)
  expect(phone.get).not.toHaveBeenCalled()
})

test("says nothing when the browser can't offer passkeys among the suggestions", async () => {
  // As a browser without an authenticator answers.
  const phone = browserWithPasskeys({ suggestions: true })
  phone.get.mockRejectedValue(new DOMException("Not supported", "NotSupportedError"))
  signedOutWithPasskeys()
  renderApp("/sign-in")

  await waitFor(() => expect(phone.get).toHaveBeenCalled())
  expect(screen.queryByRole("alert")).not.toBeInTheDocument()
  expect(screen.getByRole("button", { name: "Sign in with a passkey" })).toBeEnabled()
})

test("says nothing when the person cancels on their phone", async () => {
  const phone = browserWithPasskeys()
  phone.get.mockRejectedValue(new DOMException("The person cancelled", "NotAllowedError"))
  signedOutWithPasskeys()
  renderApp("/sign-in")

  await userEvent.click(await screen.findByRole("button", { name: "Sign in with a passkey" }))

  await waitFor(() => expect(phone.get).toHaveBeenCalled())
  expect(screen.getByRole("button", { name: "Sign in with a passkey" })).toBeEnabled()
  expect(screen.queryByRole("alert")).not.toBeInTheDocument()
})

test("says so when the server doesn't accept the passkey", async () => {
  browserWithPasskeys()
  signedOutWithPasskeys()
  server.use(http.post("*/api/session/passkey", () => problem(401, "session.passkeyNotAccepted")))
  renderApp("/sign-in")

  await userEvent.click(await screen.findByRole("button", { name: "Sign in with a passkey" }))

  expect(
    await screen.findByText(
      "This passkey wasn't accepted. Try again, or sign in with your password.",
    ),
  ).toBeInTheDocument()
})

test("still offers passkeys after signing out", async () => {
  browserWithPasskeys()
  signedInWithPasskeys([])
  server.use(http.delete("*/api/session", () => new HttpResponse(null, { status: 204 })))
  renderApp("/")

  await userEvent.click(await screen.findByRole("button", { name: "Account" }))
  await userEvent.click(await screen.findByRole("menuitem", { name: "Sign out" }))

  expect(await screen.findByRole("button", { name: "Sign in with a passkey" })).toBeInTheDocument()
})

test("offers no passkeys when the installation has none", async () => {
  browserWithPasskeys()
  server.use(
    http.get("*/api/session", () => HttpResponse.json({ passkeys: false })),
    http.get("*/api/setup", () => HttpResponse.json({ required: false })),
  )
  renderApp("/sign-in")

  expect(await screen.findByLabelText("Username")).toHaveAttribute("autocomplete", "username")
  expect(screen.queryByRole("button", { name: "Sign in with a passkey" })).not.toBeInTheDocument()
})

test("offers no passkeys in a browser without them", async () => {
  signedOutWithPasskeys()
  renderApp("/sign-in")

  await screen.findByLabelText("Username")
  expect(screen.queryByRole("button", { name: "Sign in with a passkey" })).not.toBeInTheDocument()
})

test("settings list the passkeys, and add one with a name", async () => {
  const phone = browserWithPasskeys()
  const state = signedInWithPasskeys([
    { id: "b2xk", label: "Old laptop", created: "2026-03-01T10:00:00Z" },
  ])
  const sent: unknown[] = []
  server.use(
    http.post("*/api/account/passkeys/options", () => HttpResponse.json(creationOptions)),
    http.post("*/api/account/passkeys", async ({ request }) => {
      const body = (await request.json()) as { label: string }
      sent.push(body)
      const added = { id: "bmV3", label: body.label, created: "2026-03-14T10:00:00Z" }
      state.passkeys.unshift(added)
      return HttpResponse.json(added)
    }),
  )
  renderApp("/settings")

  const card = (await screen.findByText("Passkeys")).closest("[data-slot=card]") as HTMLElement
  expect(await within(card).findByText("Old laptop")).toBeInTheDocument()
  expect(within(card).getByText("Added 1 March 2026 · not used yet")).toBeInTheDocument()

  await userEvent.click(within(card).getByRole("button", { name: "Add a passkey" }))
  const dialog = await screen.findByRole("dialog", { name: "Add a passkey" })
  await userEvent.type(within(dialog).getByLabelText("Name"), "My phone")
  await userEvent.click(within(dialog).getByRole("button", { name: "Add passkey" }))

  expect(await within(card).findByText("My phone")).toBeInTheDocument()
  expect(phone.create).toHaveBeenCalledWith({ publicKey: creationOptions })
  expect(sent).toEqual([{ label: "My phone", credential: attestation }])
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument()
  // The keyboard carries on from the button, not from the top of the page.
  await waitFor(() =>
    expect(within(card).getByRole("button", { name: "Add a passkey" })).toHaveFocus(),
  )
})

test("cancelling the dialog for a new passkey gives focus back to its button", async () => {
  browserWithPasskeys()
  signedInWithPasskeys([])
  renderApp("/settings")

  const add = await screen.findByRole("button", { name: "Add a passkey" })
  add.focus()
  await userEvent.keyboard("{Enter}")
  await screen.findByRole("dialog", { name: "Add a passkey" })
  await userEvent.keyboard("{Escape}")

  await waitFor(() => expect(add).toHaveFocus())
})

test("a passkey needs a name before the phone is asked", async () => {
  const phone = browserWithPasskeys()
  signedInWithPasskeys([])
  renderApp("/settings")

  await userEvent.click(await screen.findByRole("button", { name: "Add a passkey" }))
  const dialog = await screen.findByRole("dialog", { name: "Add a passkey" })
  await userEvent.click(within(dialog).getByRole("button", { name: "Add passkey" }))

  expect(await within(dialog).findByText("Fill this in.")).toBeInTheDocument()
  expect(phone.create).not.toHaveBeenCalled()
})

test("removes a passkey after asking", async () => {
  browserWithPasskeys()
  const state = signedInWithPasskeys([
    {
      id: "b2xk",
      label: "Old laptop",
      created: "2026-03-01T10:00:00Z",
      lastUsed: "2026-03-10T10:00:00Z",
    },
  ])
  let removed: string | undefined
  server.use(
    http.delete("*/api/account/passkeys/:id", ({ params }) => {
      removed = params.id as string
      state.passkeys = []
      return new HttpResponse(null, { status: 204 })
    }),
  )
  renderApp("/settings")

  expect(
    await screen.findByText("Added 1 March 2026 · last used 10 March 2026"),
  ).toBeInTheDocument()
  await userEvent.click(screen.getByRole("button", { name: "Remove Old laptop" }))
  const dialog = await screen.findByRole("alertdialog", {
    name: 'Remove the passkey "Old laptop"?',
  })
  await userEvent.click(within(dialog).getByRole("button", { name: "Remove passkey" }))

  expect(await screen.findByText("You have no passkeys yet.")).toBeInTheDocument()
  expect(removed).toBe("b2xk")
})

test("settings show no passkeys when the installation offers none", async () => {
  server.use(http.get("*/api/session", () => HttpResponse.json({ account: anna, passkeys: false })))
  renderApp("/settings")

  expect(await screen.findByLabelText("Current password")).toBeInTheDocument()
  expect(screen.queryByText("Passkeys")).not.toBeInTheDocument()
})
