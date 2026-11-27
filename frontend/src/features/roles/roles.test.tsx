import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { expect, test } from "vitest"
import type { Role } from "@/api/client"
import { accountsAre, adminRole, anna, memberRole, scorekeeperRole, signedInAs } from "@/test/api"
import { inside, page } from "@/test/queries"
import { renderApp } from "@/test/renderApp"
import { server } from "@/test/server"

const allRoles = [adminRole, memberRole, scorekeeperRole]

function rowOf(name: string) {
  return inside(page.getByTestIdWith("role-row", name))
}

async function openActionsFor(name: string) {
  await userEvent.click(
    await inside(await page.findByTestIdWith("role-row", name)).findByTestId("role-actions"),
  )
}

test("lists the roles and what each may do", async () => {
  signedInAs(anna)
  accountsAre([], allRoles)

  renderApp("/roles")

  await page.findByTestIdWith("role-row", "Scorekeeper")
  expect(rowOf("Admin").getByTestId("role-permissions")).toHaveTextContent("Every permission")
  expect(rowOf("Member").getByTestId("role-permissions")).toHaveTextContent(
    "Create players, Create games, Edit games, Create plays, Change plays they recorded, Change plays they took part in",
  )
  expect(rowOf("Scorekeeper").getByTestId("role-permissions")).toHaveTextContent(
    "Rename players, Create plays",
  )
})

test("the admin role can't be changed, and the member role can't be deleted", async () => {
  signedInAs(anna)
  accountsAre([], allRoles)
  renderApp("/roles")
  await page.findByTestIdWith("role-row", "Scorekeeper")

  expect(rowOf("Admin").queryByTestId("role-actions")).not.toBeInTheDocument()

  await openActionsFor("Member")
  expect(await page.findByTestId("edit-role")).toBeInTheDocument()
  expect(page.queryByTestId("delete-role")).not.toBeInTheDocument()
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

  await userEvent.click(await page.findByTestId("add-role"))
  expect(await page.findByTestId("role-dialog")).toHaveAccessibleName("Add a role")
  await userEvent.type(page.getByTestId("role-name"), "Organiser")
  await userEvent.click(page.getByTestId("permission-REMOVE_PLAYERS"))
  await userEvent.click(page.getByTestId("permission-CREATE_PLAYERS"))
  await userEvent.click(page.getByTestId("save-role"))

  expect(await page.findByTestIdWith("role-row", "Organiser")).toBeInTheDocument()
  expect(sent).toEqual({ name: "Organiser", permissions: ["REMOVE_PLAYERS", "CREATE_PLAYERS"] })
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
  await userEvent.click(await page.findByTestId("edit-role"))
  expect(await page.findByTestId("role-dialog")).toHaveAccessibleName("Edit Member")
  expect(page.getByTestId("role-name")).toBeDisabled()
  expect(page.getByTestId("permission-CREATE_PLAYERS")).toBeChecked()
  await userEvent.click(page.getByTestId("permission-CREATE_PLAYERS"))
  await userEvent.click(page.getByTestId("save-role"))

  await expect
    .poll(() => sent)
    .toEqual({
      id: memberRole.id,
      body: {
        name: "Member",
        permissions: [
          "CREATE_GAMES",
          "EDIT_GAMES",
          "CREATE_PLAYS",
          "CHANGE_PLAYS_RECORDED",
          "CHANGE_PLAYS_TAKEN_PART_IN",
        ],
      },
    })
})

test("offers only the permissions a role can hold", async () => {
  signedInAs(anna)
  accountsAre([], allRoles)
  renderApp("/roles")

  await userEvent.click(await page.findByTestId("add-role"))

  // Managing roles, accounts and invites is for admins only.
  await page.findByTestId("role-dialog")
  expect(page.getAllByTestId(/^permission-[A-Z_]+$/)).toHaveLength(11)
  expect(page.getByTestId("permission-CHANGE_ANY_PLAY")).toBeInTheDocument()
  expect(page.getByTestId("permission-USE_API_KEYS")).toBeInTheDocument()
  expect(page.getByTestId("permission-CREATE_PLAYERS")).toBeInTheDocument()
  expect(page.queryByTestId("permission-MANAGE_ROLES")).not.toBeInTheDocument()
  expect(page.queryByTestId("permission-MANAGE_ACCOUNTS")).not.toBeInTheDocument()
  expect(page.queryByTestId("permission-MANAGE_INVITES")).not.toBeInTheDocument()
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
  await userEvent.click(await page.findByTestId("delete-role"))
  expect(await page.findByTestId("confirm-dialog")).toHaveAccessibleName("Delete Scorekeeper?")
  expect(state.roles).toHaveLength(3)
  await userEvent.click(page.getByTestId("confirm"))

  await expect.poll(() => page.queryByTestIdWith("role-row", "Scorekeeper")).not.toBeInTheDocument()
})
