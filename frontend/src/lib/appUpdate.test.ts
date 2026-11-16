import { afterEach, expect, test, vi } from "vitest"
import { reloadIntoNewestVersion } from "./appUpdate"

/** A stand-in for a service worker that has its files and waits to take over. */
function waitingWorker() {
  return { state: "installed", postMessage: vi.fn(), addEventListener: vi.fn() }
}

/** Puts a stand-in for the browser's service workers in place; jsdom has none. */
function serviceWorkersAre(registration: object | undefined, controller: object | null = {}) {
  const listeners = new Map<string, () => void>()
  vi.stubGlobal("navigator", {
    serviceWorker: {
      controller,
      getRegistration: () => Promise.resolve(registration),
      addEventListener: (event: string, listener: () => void) => listeners.set(event, listener),
    },
  })
  return { newWorkerTakesOver: () => listeners.get("controllerchange")?.() }
}

afterEach(() => vi.unstubAllGlobals())

test("without a service worker, it only reloads the page", async () => {
  const reload = vi.fn()

  await reloadIntoNewestVersion(reload)

  expect(reload).toHaveBeenCalledOnce()
})

test("asks for a newer version, and reloads when there is none", async () => {
  const update = vi.fn(() => Promise.resolve())
  serviceWorkersAre({ update, installing: null, waiting: null })
  const reload = vi.fn()

  await reloadIntoNewestVersion(reload)

  expect(update).toHaveBeenCalledOnce()
  expect(reload).toHaveBeenCalledOnce()
})

test("tells a new version that waits to take over, and reloads once it has", async () => {
  const worker = waitingWorker()
  const browser = serviceWorkersAre({
    update: () => Promise.resolve(),
    installing: null,
    waiting: worker,
  })
  const reload = vi.fn()

  const reloading = reloadIntoNewestVersion(reload)
  await vi.waitFor(() => expect(worker.postMessage).toHaveBeenCalledWith({ type: "SKIP_WAITING" }))
  expect(reload).not.toHaveBeenCalled()

  browser.newWorkerTakesOver()
  await reloading
  expect(reload).toHaveBeenCalledOnce()
})

test("reloads into what the device has when the server can't be asked", async () => {
  serviceWorkersAre({
    update: () => Promise.reject(new Error("offline")),
    installing: null,
    waiting: null,
  })
  const reload = vi.fn()

  await reloadIntoNewestVersion(reload)

  expect(reload).toHaveBeenCalledOnce()
})
