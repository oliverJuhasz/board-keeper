import { useTranslation } from "react-i18next"
import { useRegisterSW } from "virtual:pwa-register/react"
import { Button } from "@/components/ui/button"

/** How often an app that stays open asks the server whether there is a new version. */
const CHECK_EVERY = 60 * 60 * 1000

/**
 * Registers the service worker (see vite.config.ts), and says when a new version of Board Keeper
 * is ready. The service worker fetches a new version in the background, but the app goes on
 * with the old one until the person chooses to reload, so nothing they are doing is lost.
 */
export function UpdateNotice() {
  const { t } = useTranslation()
  const {
    needRefresh: [newVersion, setNewVersion],
    updateServiceWorker,
  } = useRegisterSW({
    // A browser looks for a new version whenever a page of the app is opened. An app that is
    // left open on a phone may not be opened again for days, so it asks now and then as well.
    onRegisteredSW(_url, registration) {
      if (registration) {
        setInterval(() => void registration.update(), CHECK_EVERY)
      }
    },
  })

  if (!newVersion) {
    return null
  }
  return (
    <div
      role="status"
      data-testid="update-notice"
      className="fixed inset-x-4 bottom-4 z-50 mx-auto flex max-w-md items-center gap-3 rounded-lg border bg-background p-3 shadow-lg"
    >
      <p className="flex-1 text-sm">{t("app.update.available")}</p>
      <Button
        variant="ghost"
        size="sm"
        onClick={() => setNewVersion(false)}
        data-testid="update-later"
      >
        {t("app.update.later")}
      </Button>
      {/* Hands over to the new service worker, which then reloads the page. */}
      <Button size="sm" onClick={() => void updateServiceWorker(true)} data-testid="update-reload">
        {t("app.update.reload")}
      </Button>
    </div>
  )
}
