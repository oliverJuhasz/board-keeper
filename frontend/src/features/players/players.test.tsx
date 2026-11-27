import { waitFor } from "@testing-library/react"
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
import { inside, page } from "@/test/queries"
import { renderApp } from "@/test/renderApp"
import { server } from "@/test/server"

const everyone = [annaPlayer, bencePlayer, doraPlayer]
const withAccounts = [annaPlayer.id, bencePlayer.id]

function rowOf(name: string) {
  return inside(page.getByTestIdWith("player-row", name))
}

async function openActionsFor(name: string) {
  await userEvent.click(
    await inside(await page.findByTestIdWith("player-row", name)).findByTestId("player-actions"),
  )
}

test("lists the players, and shows who has an account and who you are", async () => {
  signedInAs(anna)
  playersAre(everyone, withAccounts)

  renderApp("/players")

  await page.findByTestIdWith("player-row", "Dora")
  expect(rowOf("Anna").getByTestId("you")).toHaveTextContent("You")
  expect(rowOf("Anna").getByTestId("has-account")).toHaveTextContent("Has an account")
  expect(rowOf("Bence").getByTestId("has-account")).toBeInTheDocument()
  expect(rowOf("Dora").queryByTestId("has-account")).not.toBeInTheDocument()
})

test("a member can add players but sees no other actions", async () => {
  signedInAs(bence)
  playersAre(everyone, withAccounts)

  renderApp("/players")

  await page.findByTestIdWith("player-row", "Dora")
  expect(page.getByTestId("add-player")).toBeInTheDocument()
  expect(page.queryByTestId("player-actions")).not.toBeInTheDocument()
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

  await userEvent.click(await page.findByTestId("add-player"))
  await userEvent.type(page.getByTestId("player-name"), "Zsofi")
  await userEvent.click(page.getByTestId("save-player"))

  expect(await page.findByTestIdWith("player-row", "Zsofi")).toBeInTheDocument()
  expect(page.queryByTestId("player-name-dialog")).not.toBeInTheDocument()
  // The keyboard carries on from the button, not from the top of the page.
  await waitFor(() => expect(page.getByTestId("add-player")).toHaveFocus())
})

test("cancelling the dialog for a new player gives focus back to its button", async () => {
  signedInAs(bence)
  playersAre(everyone, withAccounts)
  renderApp("/players")

  const add = await page.findByTestId("add-player")
  add.focus()
  await userEvent.keyboard("{Enter}")
  expect(await page.findByTestId("player-name-dialog")).toHaveAccessibleName("Add a player")
  await userEvent.keyboard("{Escape}")

  await waitFor(() => expect(add).toHaveFocus())
})

test("says so when a new player's name is taken, and keeps the dialog open", async () => {
  signedInAs(bence)
  playersAre(everyone, withAccounts)
  server.use(http.post("*/api/players", () => problem(409, "players.nameTaken")))
  renderApp("/players")

  await userEvent.click(await page.findByTestId("add-player"))
  await userEvent.type(page.getByTestId("player-name"), "dora")
  await userEvent.click(page.getByTestId("save-player"))

  expect(await page.findByTestId("problem")).toHaveTextContent(
    "There is already a player with this name.",
  )
  expect(page.getByTestId("player-name-dialog")).toBeInTheDocument()
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
  await userEvent.click(await page.findByTestId("rename-player"))
  const nameField = await page.findByTestId("player-name")
  expect(nameField).toHaveValue("Dora")
  await userEvent.clear(nameField)
  await userEvent.type(nameField, "Dóra")
  await userEvent.click(page.getByTestId("save-player"))

  expect(await page.findByTestIdWith("player-row", "Dóra")).toBeInTheDocument()
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
  await userEvent.click(await page.findByTestId("remove-player"))
  expect(await page.findByTestId("confirm-dialog")).toHaveAccessibleName("Remove Dora?")
  expect(state.players).toHaveLength(3)
  await userEvent.click(page.getByTestId("confirm"))

  await expect.poll(() => page.queryByTestIdWith("player-row", "Dora")).not.toBeInTheDocument()
})

test("a player with an account can be renamed, but not invited or removed", async () => {
  signedInAs(anna)
  playersAre(everyone, withAccounts)
  renderApp("/players")

  await openActionsFor("Bence")

  expect(await page.findByTestId("rename-player")).toBeInTheDocument()
  expect(page.queryByTestId("invite")).not.toBeInTheDocument()
  expect(page.queryByTestId("remove-player")).not.toBeInTheDocument()
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

  await user.click(
    await inside(await page.findByTestIdWith("player-row", "Dora")).findByTestId("player-actions"),
  )
  await user.click(await page.findByTestId("invite"))
  // An Admin may choose the Role; Member is the suggestion.
  const role = await page.findByTestId("invite-role")
  await expect
    .poll(() => (role as HTMLSelectElement).selectedOptions[0]?.textContent)
    .toBe("Member")
  await user.selectOptions(role, "Admin")
  await user.click(page.getByTestId("create-link"))

  const link = `${window.location.origin}/register/the-secret-token`
  expect(await page.findByTestId("link")).toHaveValue(link)
  expect(page.getByTestId("link-description")).toHaveTextContent(/valid until 17 November 2026/)
  expect(sent).toEqual({ playerId: doraPlayer.id, roleId: adminRole.id })

  await user.click(page.getByTestId("copy-link"))
  expect(await navigator.clipboard.readText()).toBe(link)
  expect(page.getByTestId("copy-link")).toHaveTextContent("Copied")

  await user.click(page.getByTestId("link-done"))
  expect(await rowOf("Dora").findByTestId("invited")).toHaveTextContent("Invited")
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
    await inside(await page.findByTestIdWith("player-row", "Dora")).findByTestId("invited"),
  ).toHaveTextContent("Invited")

  await openActionsFor("Dora")
  expect(await page.findByTestId("invite")).toHaveTextContent("Invite again")
  await userEvent.click(page.getByTestId("cancel-invite"))

  await expect.poll(() => rowOf("Dora").queryByTestId("invited")).not.toBeInTheDocument()
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
  await userEvent.click(await page.findByTestId("create-reset-link"))
  await userEvent.click(await page.findByTestId("create-link"))

  expect(await page.findByTestId("link")).toHaveValue(
    `${window.location.origin}/reset-password/the-reset-token`,
  )
  expect(page.getByTestId("link-description")).toHaveTextContent(/valid until 11 November 2026/)
  expect(sent).toEqual({ playerId: bencePlayer.id })
})
