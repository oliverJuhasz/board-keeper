import { createBrowserRouter, type RouteObject } from "react-router"
import { AccountsPage } from "./features/accounts/AccountsPage"
import { GamesPage } from "./features/games/GamesPage"
import { RolesPage } from "./features/roles/RolesPage"
import { ResetPasswordPage } from "./features/session/ResetPasswordPage"
import { SettingsPage } from "./features/settings/SettingsPage"
import { GameStatsPage } from "./features/stats/GameStatsPage"
import { PlayersPage } from "./features/players/PlayersPage"
import { EditPlayPage } from "./features/plays/EditPlayPage"
import { PlayPage } from "./features/plays/PlayPage"
import { PlaysPage } from "./features/plays/PlaysPage"
import { RecordPlayPage } from "./features/plays/RecordPlayPage"
import { RegisterPage } from "./features/session/RegisterPage"
import { RedirectIfSignedIn, RequireSignedIn } from "./features/session/RequireSignedIn"
import { SetupPage } from "./features/session/SetupPage"
import { SignInPage } from "./features/session/SignInPage"
import { AppLayout } from "./routes/AppLayout"
import { NotFoundPage } from "./routes/NotFoundPage"
import { PublicLayout } from "./routes/PublicLayout"

// Which page is shown for which address.
export const routes: RouteObject[] = [
  {
    // Pages for people who aren't signed in.
    element: <PublicLayout />,
    children: [
      {
        path: "/sign-in",
        element: (
          <RedirectIfSignedIn>
            <SignInPage />
          </RedirectIfSignedIn>
        ),
      },
      { path: "/setup", element: <SetupPage /> },
      { path: "/register/:token", element: <RegisterPage /> },
      { path: "/reset-password/:token", element: <ResetPasswordPage /> },
    ],
  },
  {
    // Everything else needs a signed-in Account and is drawn inside AppLayout.
    path: "/",
    element: (
      <RequireSignedIn>
        <AppLayout />
      </RequireSignedIn>
    ),
    children: [
      { index: true, element: <GamesPage /> },
      { path: "games/:id", element: <GameStatsPage /> },
      { path: "players", element: <PlayersPage /> },
      { path: "plays", element: <PlaysPage /> },
      { path: "plays/new", element: <RecordPlayPage /> },
      { path: "plays/:id", element: <PlayPage /> },
      { path: "plays/:id/edit", element: <EditPlayPage /> },
      { path: "accounts", element: <AccountsPage /> },
      { path: "roles", element: <RolesPage /> },
      { path: "settings", element: <SettingsPage /> },
      { path: "*", element: <NotFoundPage /> },
    ],
  },
]

export const router = createBrowserRouter(routes)
