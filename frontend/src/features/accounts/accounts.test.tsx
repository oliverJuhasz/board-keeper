import { screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { expect, test } from "vitest"
import {
  accountsAre,
  adminRole,
  anna,
  annaAccount,
  bence,
  benceAccount,
  memberRole,
  problem,
  signedInAs,
} from "@/test/api"
import { renderApp } from "@/test/renderApp"
import { server } from "@/test/server"

function rowOf(username: string) {
  return screen.getByRole("row", { name: new RegExp(username) })
}

async function openActionsFor(username: string) {
  await userEvent.click(await screen.findByRole("button", { name: `Actions for ${username}` }))
}

test("lists the accounts with their players and roles", async () => {
  signedInAs(anna)
  accountsAre([annaAccount, { ...benceAccount, disabled: true }])

  renderApp("/accounts")

  await screen.findByRole("row", { name: /bence/ })
  expect(within(rowOf("anna")).getByText("You")).toBeInTheDocument()
  expect(within(rowOf("anna")).getByText("Anna")).toBeInTheDocument()
  expect(within(rowOf("anna")).getByText("Admin")).toBeInTheDocument()
  expect(within(rowOf("bence")).getByText("Member")).toBeInTheDocument()
  expect(within(rowOf("bence")).getByText("Disabled")).toBeInTheDocument()
})

test("changes an account's roles", async () => {
  signedInAs(anna)
  const state = accountsAre([annaAccount, benceAccount])
  let sent: unknown
  server.use(
    http.put("*/api/accounts/:id/roles", async ({ request, params }) => {
      sent = { id: params.id, body: await request.json() }
      state.accounts[1] = { ...benceAccount, roleIds: [memberRole.id, adminRole.id] }
      return new HttpResponse(null, { status: 204 })
    }),
  )
  renderApp("/accounts")

  await openActionsFor("bence")
  await userEvent.click(await screen.findByRole("menuitem", { name: "Change roles" }))
  const dialog = await screen.findByRole("dialog", { name: "Roles of bence" })
  expect(within(dialog).getByRole("checkbox", { name: "Member" })).toBeChecked()
  await userEvent.click(within(dialog).getByRole("checkbox", { name: "Admin" }))
  await userEvent.click(within(dialog).getByRole("button", { name: "Save roles" }))

  expect(await within(rowOf("bence")).findByText("Admin")).toBeInTheDocument()
  expect(sent).toEqual({ id: bence.id, body: { roleIds: [memberRole.id, adminRole.id] } })
})

test("can't give a role that holds more than you do", async () => {
  // Someone who may manage accounts, but is no admin.
  signedInAs({ ...bence, permissions: [...bence.permissions, "MANAGE_ACCOUNTS"] })
  accountsAre([annaAccount, benceAccount])
  renderApp("/accounts")

  await openActionsFor("anna")
  await userEvent.click(await screen.findByRole("menuitem", { name: "Change roles" }))

  const dialog = await screen.findByRole("dialog")
  // The checkbox is not a native input, so being disabled shows as aria-disabled.
  expect(within(dialog).getByRole("checkbox", { name: "Admin" })).toHaveAttribute(
    "aria-disabled",
    "true",
  )
  expect(within(dialog).getByText("Holds permissions you don't have.")).toBeInTheDocument()
  expect(within(dialog).getByRole("checkbox", { name: "Member" })).not.toHaveAttribute(
    "aria-disabled",
    "true",
  )
})

test("disables an account only after asking, and can enable it again", async () => {
  signedInAs(anna)
  const state = accountsAre([annaAccount, benceAccount])
  const sent: unknown[] = []
  server.use(
    http.put("*/api/accounts/:id/disabled", async ({ request }) => {
      const body = (await request.json()) as { disabled: boolean }
      sent.push(body)
      state.accounts[1] = { ...benceAccount, disabled: body.disabled }
      return new HttpResponse(null, { status: 204 })
    }),
  )
  renderApp("/accounts")

  await openActionsFor("bence")
  await userEvent.click(await screen.findByRole("menuitem", { name: "Disable" }))
  expect(await screen.findByRole("alertdialog", { name: "Disable bence?" })).toBeInTheDocument()
  expect(sent).toEqual([])
  await userEvent.click(screen.getByRole("button", { name: "Disable account" }))
  expect(await within(rowOf("bence")).findByText("Disabled")).toBeInTheDocument()

  await openActionsFor("bence")
  await userEvent.click(await screen.findByRole("menuitem", { name: "Enable" }))
  await expect.poll(() => within(rowOf("bence")).queryByText("Disabled")).not.toBeInTheDocument()
  expect(sent).toEqual([{ disabled: true }, { disabled: false }])
})

test("offers no way to disable your own account", async () => {
  signedInAs(anna)
  accountsAre([annaAccount, benceAccount])
  renderApp("/accounts")

  await openActionsFor("anna")

  expect(await screen.findByRole("menuitem", { name: "Change roles" })).toBeInTheDocument()
  expect(screen.queryByRole("menuitem", { name: "Disable" })).not.toBeInTheDocument()
})

test("explains why the last admin can't lose the admin role", async () => {
  signedInAs(anna)
  accountsAre([annaAccount, benceAccount])
  server.use(http.put("*/api/accounts/:id/roles", () => problem(409, "accounts.lastAdmin")))
  renderApp("/accounts")

  await openActionsFor("anna")
  await userEvent.click(await screen.findByRole("menuitem", { name: "Change roles" }))
  const dialog = await screen.findByRole("dialog")
  await userEvent.click(within(dialog).getByRole("checkbox", { name: "Admin" }))
  await userEvent.click(within(dialog).getByRole("button", { name: "Save roles" }))

  expect(
    await screen.findByText("There has to be at least one admin who can sign in."),
  ).toBeInTheDocument()
})
