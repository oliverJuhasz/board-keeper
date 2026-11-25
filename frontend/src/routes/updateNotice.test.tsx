import { render } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { expect, test, vi } from "vitest"
import { page } from "@/test/queries"
import { UpdateNotice } from "./UpdateNotice"

// A stand-in for the service worker's registration, as if a new version had been fetched.
// vi.hoisted makes these exist before vi.mock, which Vitest moves to the top of the file.
const registration = vi.hoisted(() => ({
  newVersion: true,
  setNewVersion: vi.fn(),
  updateServiceWorker: vi.fn(() => Promise.resolve()),
}))

vi.mock("virtual:pwa-register/react", () => ({
  useRegisterSW: () => ({
    needRefresh: [registration.newVersion, registration.setNewVersion],
    offlineReady: [false, vi.fn()],
    updateServiceWorker: registration.updateServiceWorker,
  }),
}))

test("says when a new version is ready, and reloads into it only when asked", async () => {
  render(<UpdateNotice />)

  expect(page.getByTestId("update-notice")).toHaveTextContent(
    "A new version of Board Keeper is ready.",
  )
  expect(registration.updateServiceWorker).not.toHaveBeenCalled()

  await userEvent.click(page.getByTestId("update-reload"))
  expect(registration.updateServiceWorker).toHaveBeenCalledWith(true)
})

test("can be put off until later", async () => {
  render(<UpdateNotice />)

  await userEvent.click(page.getByTestId("update-later"))

  expect(registration.setNewVersion).toHaveBeenCalledWith(false)
})

test("says nothing while there is no new version", () => {
  registration.newVersion = false

  render(<UpdateNotice />)

  expect(page.queryByTestId("update-notice")).not.toBeInTheDocument()
})
