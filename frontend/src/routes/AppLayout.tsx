import { Link, Outlet } from "react-router"
import { AccountMenu } from "@/features/session/AccountMenu"
import { useAccountLanguage } from "@/features/session/useChangeLanguage"
import { LanguageSelect } from "@/i18n/LanguageSelect"

/** The frame around every signed-in page. `<Outlet />` is where the current page is drawn. */
export function AppLayout() {
  useAccountLanguage()

  return (
    <div className="min-h-svh">
      <header className="border-b">
        <div className="mx-auto flex h-14 max-w-3xl items-center justify-between gap-2 px-4">
          <Link to="/" className="font-heading text-lg font-semibold">
            Board Keeper
          </Link>
          <div className="flex items-center gap-1">
            <LanguageSelect />
            <AccountMenu />
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-3xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  )
}
