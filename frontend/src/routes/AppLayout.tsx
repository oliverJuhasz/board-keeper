import { useTranslation } from "react-i18next"
import { Link, NavLink, Outlet, useMatch } from "react-router"
import { useSendWaitingPlays } from "@/features/plays/sendWaitingPlays"
import { AccountMenu } from "@/features/session/AccountMenu"
import { useAccountLanguage } from "@/features/session/useChangeLanguage"
import { useOnline } from "@/lib/online"

/** The frame around every signed-in page. `<Outlet />` is where the current page is drawn. */
export function AppLayout() {
  const { t } = useTranslation()
  useAccountLanguage()
  // Plays recorded on this device without a connection are sent from here, on every page.
  useSendWaitingPlays()
  const online = useOnline()
  // A Game's statistics belong with the Games, though their address doesn't start with the
  // start page's "/". useMatch says whether the current address fits a pattern.
  const onGamePage = useMatch("/games/*") !== null

  // NavLink is a link that knows whether it points at the current page.
  const navLink = ({ isActive }: { isActive: boolean }) =>
    isActive ? "text-foreground" : "text-muted-foreground hover:text-foreground"

  return (
    <div className="min-h-svh">
      <header className="border-b">
        <div className="mx-auto flex h-14 max-w-3xl items-center justify-between gap-2 px-4">
          {/* The name stays on one line on a phone 360 pixels wide or more. Below that there
              is no room for it beside the menu, so it may wrap. "min-[360px]:" is Tailwind's way
              to give a breakpoint of its own where the standard ones ("sm:" at 640) don't fit. */}
          <div className="flex items-center gap-3 min-[400px]:gap-4 sm:gap-5">
            <Link
              to="/"
              className="font-heading text-lg font-semibold min-[360px]:shrink-0 min-[360px]:whitespace-nowrap"
            >
              Board Keeper
            </Link>
            <nav className="flex gap-3 text-sm font-medium sm:gap-4">
              <NavLink
                to="/"
                end
                className={({ isActive }) => navLink({ isActive: isActive || onGamePage })}
              >
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
      {!online && (
        <p role="status" className="bg-muted px-4 py-2 text-center text-sm text-muted-foreground">
          {t("app.offline")}
        </p>
      )}
      <main className="mx-auto max-w-3xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  )
}
