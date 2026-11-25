import { waitFor } from "@testing-library/react"
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
import { inside, page } from "@/test/queries"
import { renderApp } from "@/test/renderApp"
import { server } from "@/test/server"

function rowOf(username: string) {
  return inside(page.getByTestIdWith("account-row", username))
}

async function openActionsFor(username: string) {
  await userEvent.click(
    await inside(await page.findByTestIdWith("account-row", username)).findByTestId(
      "account-actions",
    ),
  )
}

test("lists the accounts with their players and roles", async () => {
  signedInAs(anna)
  accountsAre([annaAccount, { ...benceAccount, disabled: true }])

  renderApp("/accounts")

  await page.findByTestIdWith("account-row", "bence")
  expect(rowOf("anna").getByTestId("you")).toHaveTextContent("You")
  expect(rowOf("anna").getByTestId("player")).toHaveTextContent("Anna")
  expect(rowOf("anna").getByTestId("roles")).toHaveTextContent("Admin")
  expect(rowOf("bence").getByTestId("roles")).toHaveTextContent("Member")
  expect(rowOf("bence").getByTestId("disabled")).toHaveTextContent("Disabled")
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
  await userEvent.click(await page.findByTestId("change-roles"))
  expect(await page.findByTestId("account-roles-dialog")).toHaveAccessibleName("Roles of bence")
  expect(page.getByTestId(`account-role-${memberRole.id}`)).toBeChecked()
  await userEvent.click(page.getByTestId(`account-role-${adminRole.id}`))
  await userEvent.click(page.getByTestId("save-roles"))

  await waitFor(() => expect(rowOf("bence").getByTestId("roles")).toHaveTextContent("Admin"))
  expect(sent).toEqual({ id: bence.id, body: { roleIds: [memberRole.id, adminRole.id] } })
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
  await userEvent.click(await page.findByTestId("disable-account"))
  expect(await page.findByTestId("confirm-dialog")).toHaveAccessibleName("Disable bence?")
  expect(sent).toEqual([])
  await userEvent.click(page.getByTestId("confirm"))
  expect(
    await inside(await page.findByTestIdWith("account-row", "bence")).findByTestId("disabled"),
  ).toBeInTheDocument()

  await openActionsFor("bence")
  await userEvent.click(await page.findByTestId("enable-account"))
  await expect.poll(() => rowOf("bence").queryByTestId("disabled")).not.toBeInTheDocument()
  expect(sent).toEqual([{ disabled: true }, { disabled: false }])
})

test("offers no way to disable your own account", async () => {
  signedInAs(anna)
  accountsAre([annaAccount, benceAccount])
  renderApp("/accounts")

  await openActionsFor("anna")

  expect(await page.findByTestId("change-roles")).toBeInTheDocument()
  expect(page.queryByTestId("disable-account")).not.toBeInTheDocument()
})

test("explains why the last admin can't lose the admin role", async () => {
  signedInAs(anna)
  accountsAre([annaAccount, benceAccount])
  server.use(http.put("*/api/accounts/:id/roles", () => problem(409, "accounts.lastAdmin")))
  renderApp("/accounts")

  await openActionsFor("anna")
  await userEvent.click(await page.findByTestId("change-roles"))
  await userEvent.click(await page.findByTestId(`account-role-${adminRole.id}`))
  await userEvent.click(page.getByTestId("save-roles"))

  expect(
    await page.findByTestIdWith("problem", "There has to be at least one admin who can sign in."),
  ).toBeInTheDocument()
})
