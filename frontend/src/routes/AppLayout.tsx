import { useTranslation } from "react-i18next"
import { Link, NavLink, Outlet } from "react-router"
import { AccountMenu } from "@/features/session/AccountMenu"
import { useAccountLanguage } from "@/features/session/useChangeLanguage"

/** The frame around every signed-in page. `<Outlet />` is where the current page is drawn. */
export function AppLayout() {
  const { t } = useTranslation()
  useAccountLanguage()

  // NavLink is a link that knows whether it points at the current page.
  const navLink = ({ isActive }: { isActive: boolean }) =>
    isActive ? "text-foreground" : "text-muted-foreground hover:text-foreground"

  return (
    <div className="min-h-svh">
      <header className="border-b">
        <div className="mx-auto flex h-14 max-w-3xl items-center justify-between gap-2 px-4">
          <div className="flex items-center gap-5">
            <Link to="/" className="font-heading text-lg font-semibold">
              Board Keeper
            </Link>
            <nav className="flex gap-4 text-sm font-medium">
              <NavLink to="/" end className={navLink}>
                {t("nav.games")}
              </NavLink>
              <NavLink to="/plays" className={navLink}>
                {t("nav.plays")}
              </NavLink>
              <NavLink to="/players" className={navLink}>
                {t("nav.players")}
              </NavLink>
            </nav>
          </div>
          <AccountMenu />
        </div>
      </header>
      <main className="mx-auto max-w-3xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  )
}
