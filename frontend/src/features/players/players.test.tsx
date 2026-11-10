import { screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { expect, test } from "vitest"
import {
  adminRole,
  anna,
  annaPlayer,
  bence,
  bencePlayer,
  doraPlayer,
  playersAre,
  problem,
  signedInAs,
} from "@/test/api"
import { renderApp } from "@/test/renderApp"
import { server } from "@/test/server"

const everyone = [annaPlayer, bencePlayer, doraPlayer]
const withAccounts = [annaPlayer.id, bencePlayer.id]

function rowOf(name: string) {
  return screen.getByRole("row", { name: new RegExp(name) })
}

async function openActionsFor(name: string) {
  await userEvent.click(await screen.findByRole("button", { name: `Actions for ${name}` }))
}

test("lists the players, and shows who has an account and who you are", async () => {
  signedInAs(anna)
  playersAre(everyone, withAccounts)

  renderApp("/players")

  await screen.findByRole("row", { name: /Dora/ })
  expect(within(rowOf("Anna")).getByText("You")).toBeInTheDocument()
  expect(within(rowOf("Anna")).getByText("Has an account")).toBeInTheDocument()
  expect(within(rowOf("Bence")).getByText("Has an account")).toBeInTheDocument()
  expect(within(rowOf("Dora")).queryByText("Has an account")).not.toBeInTheDocument()
})

test("a member can add players but sees no other actions", async () => {
  signedInAs(bence)
  playersAre(everyone, withAccounts)

  renderApp("/players")

  await screen.findByRole("row", { name: /Dora/ })
  expect(screen.getByRole("button", { name: "Add player" })).toBeInTheDocument()
  expect(screen.queryByRole("button", { name: /Actions for/ })).not.toBeInTheDocument()
})

test("adds a player", async () => {
  signedInAs(bence)
  const state = playersAre(everyone, withAccounts)
  server.use(
    http.post("*/api/players", async ({ request }) => {
      const { name } = (await request.json()) as { name: string }
      const created = { id: "0193a000-0000-7000-8000-0000000000b9", name }
      state.players.push(created)
      return HttpResponse.json(created)
    }),
  )
  renderApp("/players")

  await userEvent.click(await screen.findByRole("button", { name: "Add player" }))
  await userEvent.type(screen.getByLabelText("Name"), "Zsofi")
  await userEvent.click(
    within(screen.getByRole("dialog")).getByRole("button", { name: "Add player" }),
  )

  expect(await screen.findByRole("row", { name: /Zsofi/ })).toBeInTheDocument()
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument()
})

test("says so when a new player's name is taken, and keeps the dialog open", async () => {
  signedInAs(bence)
  playersAre(everyone, withAccounts)
  server.use(http.post("*/api/players", () => problem(409, "players.nameTaken")))
  renderApp("/players")

  await userEvent.click(await screen.findByRole("button", { name: "Add player" }))
  await userEvent.type(screen.getByLabelText("Name"), "dora")
  await userEvent.click(
    within(screen.getByRole("dialog")).getByRole("button", { name: "Add player" }),
  )

  expect(await screen.findByText("There is already a player with this name.")).toBeInTheDocument()
  expect(screen.getByRole("dialog")).toBeInTheDocument()
})

test("renames a player", async () => {
  signedInAs(anna)
  const state = playersAre(everyone, withAccounts)
  server.use(
    http.put("*/api/players/:id", async ({ request, params }) => {
      const { name } = (await request.json()) as { name: string }
      state.players = state.players.map((player) =>
        player.id === params.id ? { ...player, name } : player,
      )
      return HttpResponse.json({ id: params.id, name })
    }),
  )
  renderApp("/players")

  await openActionsFor("Dora")
  await userEvent.click(await screen.findByRole("menuitem", { name: "Rename" }))
  const nameField = await screen.findByLabelText("Name")
  expect(nameField).toHaveValue("Dora")
  await userEvent.clear(nameField)
  await userEvent.type(nameField, "Dóra")
  await userEvent.click(screen.getByRole("button", { name: "Rename" }))

  expect(await screen.findByRole("row", { name: /Dóra/ })).toBeInTheDocument()
})

test("removes a player only after asking", async () => {
  signedInAs(anna)
  const state = playersAre(everyone, withAccounts)
  server.use(
    http.delete("*/api/players/:id", ({ params }) => {
      state.players = state.players.filter((player) => player.id !== params.id)
      return new HttpResponse(null, { status: 204 })
    }),
  )
  renderApp("/players")

  await openActionsFor("Dora")
  await userEvent.click(await screen.findByRole("menuitem", { name: "Remove" }))
  expect(await screen.findByRole("alertdialog", { name: "Remove Dora?" })).toBeInTheDocument()
  expect(state.players).toHaveLength(3)
  await userEvent.click(screen.getByRole("button", { name: "Remove player" }))

  await expect.poll(() => screen.queryByRole("row", { name: /Dora/ })).not.toBeInTheDocument()
})

test("a player with an account can be renamed, but not invited or removed", async () => {
  signedInAs(anna)
  playersAre(everyone, withAccounts)
  renderApp("/players")

  await openActionsFor("Bence")

  expect(await screen.findByRole("menuitem", { name: "Rename" })).toBeInTheDocument()
  expect(screen.queryByRole("menuitem", { name: "Invite" })).not.toBeInTheDocument()
  expect(screen.queryByRole("menuitem", { name: "Remove" })).not.toBeInTheDocument()
})

test("invites a player and shows the link to send", async () => {
  signedInAs(anna)
  const state = playersAre(everyone, withAccounts)
  let sent: unknown
  server.use(
    http.post("*/api/invites", async ({ request }) => {
      sent = await request.json()
      const invite = {
        id: "0193a000-0000-7000-8000-0000000000d1",
        playerId: doraPlayer.id,
        roleId: adminRole.id,
        expiresAt: "2026-11-17T12:00:00Z",
        expired: false,
      }
      state.invites.push(invite)
      return HttpResponse.json({ invite, token: "the-secret-token" })
    }),
  )
  const user = userEvent.setup()
  renderApp("/players")

  await user.click(await screen.findByRole("button", { name: "Actions for Dora" }))
  await user.click(await screen.findByRole("menuitem", { name: "Invite" }))
  // An Admin may choose the Role; Member is the suggestion.
  const role = await screen.findByLabelText("Role")
  await expect
    .poll(() => (role as HTMLSelectElement).selectedOptions[0]?.textContent)
    .toBe("Member")
  await user.selectOptions(role, "Admin")
  await user.click(screen.getByRole("button", { name: "Create invite link" }))

  const link = `${window.location.origin}/register/the-secret-token`
  expect(await screen.findByLabelText("Invite link")).toHaveValue(link)
  expect(screen.getByText(/valid until 17 November 2026/)).toBeInTheDocument()
  expect(sent).toEqual({ playerId: doraPlayer.id, roleId: adminRole.id })

  await user.click(screen.getByRole("button", { name: "Copy link" }))
  expect(await navigator.clipboard.readText()).toBe(link)
  expect(screen.getByRole("button", { name: "Copied" })).toBeInTheDocument()

  await user.click(screen.getByRole("button", { name: "Done" }))
  expect(await within(rowOf("Dora")).findByText("Invited")).toBeInTheDocument()
})

test("cancels an invite", async () => {
  signedInAs(anna)
  const invite = {
    id: "0193a000-0000-7000-8000-0000000000d1",
    playerId: doraPlayer.id,
    roleId: adminRole.id,
    expiresAt: "2026-11-17T12:00:00Z",
    expired: false,
  }
  const state = playersAre(everyone, withAccounts, [invite])
  server.use(
    http.delete("*/api/invites/:id", () => {
      state.invites = []
      return new HttpResponse(null, { status: 204 })
    }),
  )
  renderApp("/players")
  expect(
    await within(await screen.findByRole("row", { name: /Dora/ })).findByText("Invited"),
  ).toBeInTheDocument()

  await openActionsFor("Dora")
  expect(await screen.findByRole("menuitem", { name: "Invite again" })).toBeInTheDocument()
  await userEvent.click(screen.getByRole("menuitem", { name: "Cancel invite" }))

  await expect.poll(() => within(rowOf("Dora")).queryByText("Invited")).not.toBeInTheDocument()
})

test("creates a password reset link for a player with an account", async () => {
  signedInAs(anna)
  playersAre(everyone, withAccounts)
  let sent: unknown
  server.use(
    http.post("*/api/reset-links", async ({ request }) => {
      sent = await request.json()
      return HttpResponse.json({ token: "the-reset-token", expiresAt: "2026-11-11T14:30:00Z" })
    }),
  )
  renderApp("/players")

  await openActionsFor("Bence")
  await userEvent.click(await screen.findByRole("menuitem", { name: "Create password reset link" }))
  await userEvent.click(await screen.findByRole("button", { name: "Create reset link" }))

  expect(await screen.findByLabelText("Reset link")).toHaveValue(
    `${window.location.origin}/reset-password/the-reset-token`,
  )
  expect(screen.getByText(/valid until 11 November 2026/)).toBeInTheDocument()
  expect(sent).toEqual({ playerId: bencePlayer.id })
})
