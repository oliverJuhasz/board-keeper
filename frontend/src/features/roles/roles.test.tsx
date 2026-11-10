import { screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { expect, test } from "vitest"
import type { Role } from "@/api/client"
import {
  accountsAre,
  adminRole,
  anna,
  bence,
  memberRole,
  scorekeeperRole,
  signedInAs,
} from "@/test/api"
import { renderApp } from "@/test/renderApp"
import { server } from "@/test/server"

const allRoles = [adminRole, memberRole, scorekeeperRole]

function rowOf(name: string) {
  return screen.getByRole("row", { name: new RegExp(name) })
}

async function openActionsFor(name: string) {
  await userEvent.click(await screen.findByRole("button", { name: `Actions for ${name}` }))
}

test("lists the roles and what each may do", async () => {
  signedInAs(anna)
  accountsAre([], allRoles)

  renderApp("/roles")

  await screen.findByRole("row", { name: /Scorekeeper/ })
  expect(within(rowOf("Admin")).getByText("Every permission")).toBeInTheDocument()
  expect(within(rowOf("Member")).getByText("Create players, Create plays")).toBeInTheDocument()
  expect(within(rowOf("Scorekeeper")).getByText("Rename players, Create plays")).toBeInTheDocument()
})

test("the admin role can't be changed, and the member role can't be deleted", async () => {
  signedInAs(anna)
  accountsAre([], allRoles)
  renderApp("/roles")
  await screen.findByRole("row", { name: /Scorekeeper/ })

  expect(screen.queryByRole("button", { name: "Actions for Admin" })).not.toBeInTheDocument()

  await openActionsFor("Member")
  expect(await screen.findByRole("menuitem", { name: "Edit" })).toBeInTheDocument()
  expect(screen.queryByRole("menuitem", { name: "Delete" })).not.toBeInTheDocument()
})

test("adds a role with the ticked permissions", async () => {
  signedInAs(anna)
  const state = accountsAre([], allRoles)
  let sent: unknown
  server.use(
    http.post("*/api/roles", async ({ request }) => {
      const body = (await request.json()) as Pick<Role, "name" | "permissions">
      sent = body
      const created = { id: "0193a000-0000-7000-8000-0000000000c9", ...body }
      state.roles.push(created)
      return HttpResponse.json(created)
    }),
  )
  renderApp("/roles")

  await userEvent.click(await screen.findByRole("button", { name: "Add role" }))
  const dialog = await screen.findByRole("dialog", { name: "Add a role" })
  await userEvent.type(within(dialog).getByLabelText("Name"), "Organiser")
  await userEvent.click(within(dialog).getByRole("checkbox", { name: "Manage invites" }))
  await userEvent.click(within(dialog).getByRole("checkbox", { name: "Create players" }))
  await userEvent.click(within(dialog).getByRole("button", { name: "Add role" }))

  expect(await screen.findByRole("row", { name: /Organiser/ })).toBeInTheDocument()
  expect(sent).toEqual({ name: "Organiser", permissions: ["MANAGE_INVITES", "CREATE_PLAYERS"] })
})

test("edits the member role's permissions, but not its name", async () => {
  signedInAs(anna)
  accountsAre([], allRoles)
  let sent: unknown
  server.use(
    http.put("*/api/roles/:id", async ({ request, params }) => {
      sent = { id: params.id, body: await request.json() }
      return HttpResponse.json({ ...memberRole, permissions: ["CREATE_PLAYS"] })
    }),
  )
  renderApp("/roles")

  await openActionsFor("Member")
  await userEvent.click(await screen.findByRole("menuitem", { name: "Edit" }))
  const dialog = await screen.findByRole("dialog", { name: "Edit Member" })
  expect(within(dialog).getByLabelText("Name")).toBeDisabled()
  expect(within(dialog).getByRole("checkbox", { name: "Create players" })).toBeChecked()
  await userEvent.click(within(dialog).getByRole("checkbox", { name: "Create players" }))
  await userEvent.click(within(dialog).getByRole("button", { name: "Save role" }))

  await expect
    .poll(() => sent)
    .toEqual({
      id: memberRole.id,
      body: { name: "Member", permissions: ["CREATE_PLAYS"] },
    })
})

test("can't tick a permission you don't have yourself", async () => {
  signedInAs({ ...bence, permissions: [...bence.permissions, "MANAGE_ROLES"] })
  accountsAre([], allRoles)
  renderApp("/roles")

  await userEvent.click(await screen.findByRole("button", { name: "Add role" }))

  const dialog = await screen.findByRole("dialog")
  expect(within(dialog).getByRole("checkbox", { name: "Manage accounts" })).toHaveAttribute(
    "aria-disabled",
    "true",
  )
  expect(within(dialog).getByRole("checkbox", { name: "Create players" })).not.toHaveAttribute(
    "aria-disabled",
    "true",
  )
})

test("deletes a role only after asking", async () => {
  signedInAs(anna)
  const state = accountsAre([], allRoles)
  server.use(
    http.delete("*/api/roles/:id", ({ params }) => {
      state.roles = state.roles.filter((role) => role.id !== params.id)
      return new HttpResponse(null, { status: 204 })
    }),
  )
  renderApp("/roles")

  await openActionsFor("Scorekeeper")
  await userEvent.click(await screen.findByRole("menuitem", { name: "Delete" }))
  expect(
    await screen.findByRole("alertdialog", { name: "Delete Scorekeeper?" }),
  ).toBeInTheDocument()
  expect(state.roles).toHaveLength(3)
  await userEvent.click(screen.getByRole("button", { name: "Delete role" }))

  await expect
    .poll(() => screen.queryByRole("row", { name: /Scorekeeper/ }))
    .not.toBeInTheDocument()
})
