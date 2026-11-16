/**
 * The commit this copy of the app was built from, if the build knew it (`appVersion()` in
 * vite.config.ts). The server tells its own in GET /api/about; where the two differ, the device
 * still has the app of another version.
 */
export function appCommit(): string | undefined {
  return /^[0-9a-f]{40}$/.test(__APP_VERSION__) ? __APP_VERSION__ : undefined
}

/**
 * Reloads the page into the newest version of the app, fetching it first if the device doesn't
 * have it yet.
 *
 * The app's files are kept on the device by the service worker (see vite.config.ts), so reloading
 * the page alone would draw the same version again. A new version comes with a new service
 * worker, which waits until it is told to take over; UpdateNotice does the same once the browser
 * has found a new version by itself.
 *
 * @param reload what reloads the page, for tests to replace
 */
export async function reloadIntoNewestVersion(reload = () => window.location.reload()) {
  // Without a service worker, as in development, the browser fetches the app anew anyway.
  const registration =
    "serviceWorker" in navigator ? await navigator.serviceWorker.getRegistration() : undefined
  if (registration) {
    // Asks the server for a newer service worker, as the browser does when the app is opened.
    // Offline that fails, and there is nothing newer to wait for.
    await registration.update().catch(() => undefined)
    const next = registration.installing ?? registration.waiting
    if (next && navigator.serviceWorker.controller) {
      await takenOverBy(next)
    }
  }
  reload()
}

/** Tells a new service worker to take over once it has its files, and waits until it has. */
function takenOverBy(worker: ServiceWorker) {
  return new Promise<void>((resolve) => {
    // The browser says so when another service worker starts serving this page.
    navigator.serviceWorker.addEventListener("controllerchange", () => resolve(), { once: true })
    const proceed = () => {
      if (worker.state === "installed") {
        // The message Workbox's service worker waits for before it replaces the old one.
        worker.postMessage({ type: "SKIP_WAITING" })
      } else if (worker.state === "activated" || worker.state === "redundant") {
        // It has taken over already, or it couldn't fetch its files.
        resolve()
      }
    }
    worker.addEventListener("statechange", proceed)
    proceed()
  })
}
