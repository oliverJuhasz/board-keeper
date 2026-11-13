import { createBrowserRouter, Outlet, type RouteObject } from "react-router"
import { RedirectIfSignedIn, RequireSignedIn } from "./features/session/RequireSignedIn"
import { AppLayout } from "./routes/AppLayout"
import { AppLoading } from "./routes/AppLoading"
import { NotFoundPage } from "./routes/NotFoundPage"
import { PublicLayout } from "./routes/PublicLayout"

// Which page is shown for which address.
//
// Each page's code is loaded the first time it is needed, not up front: `import(...)` written as
// a function call is a dynamic import, and the build puts what it imports into a file of its
// own. The router fetches that file before it moves to the page, so the page that is showing
// stays until the next one is ready. The frames around the pages (the layouts) and the checks
// for being signed in are small and needed everywhere, so they are part of the first file.
//
// The list is made by a function, so that each router gets objects of its own: React Router
// keeps what it loaded for a page on the page's route object, and a second router built from
// the same objects would not find the page. The app has one router; the tests make one each.
export function appRoutes(): RouteObject[] {
  return [
    {
      // Pages for people who aren't signed in.
      element: <PublicLayout />,
      // Shown while the first page's code loads, when the app has just been opened.
      HydrateFallback: AppLoading,
      children: [
        {
          element: (
            <RedirectIfSignedIn>
              <Outlet />
            </RedirectIfSignedIn>
          ),
          children: [
            {
              path: "/sign-in",
              lazy: {
                Component: async () => (await import("./features/session/SignInPage")).SignInPage,
              },
            },
          ],
        },
        {
          path: "/setup",
          lazy: { Component: async () => (await import("./features/session/SetupPage")).SetupPage },
        },
        {
          path: "/register/:token",
          lazy: {
            Component: async () => (await import("./features/session/RegisterPage")).RegisterPage,
          },
        },
        {
          path: "/reset-password/:token",
          lazy: {
            Component: async () =>
              (await import("./features/session/ResetPasswordPage")).ResetPasswordPage,
          },
        },
      ],
    },
    {
      // Everything else needs a signed-in Account and is drawn inside AppLayout.
      path: "/",
      HydrateFallback: AppLoading,
      element: (
        <RequireSignedIn>
          <AppLayout />
        </RequireSignedIn>
      ),
      children: [
        {
          index: true,
          lazy: { Component: async () => (await import("./features/games/GamesPage")).GamesPage },
        },
        {
          path: "games/:id",
          lazy: {
            Component: async () => (await import("./features/stats/GameStatsPage")).GameStatsPage,
          },
        },
        {
          path: "players",
          lazy: {
            Component: async () => (await import("./features/players/PlayersPage")).PlayersPage,
          },
        },
        {
          path: "players/:id",
          lazy: {
            Component: async () =>
              (await import("./features/stats/PlayerStatsPage")).PlayerStatsPage,
          },
        },
        {
          path: "plays",
          lazy: { Component: async () => (await import("./features/plays/PlaysPage")).PlaysPage },
        },
        {
          path: "plays/new",
          lazy: {
            Component: async () => (await import("./features/plays/RecordPlayPage")).RecordPlayPage,
          },
        },
        {
          path: "plays/:id",
          lazy: { Component: async () => (await import("./features/plays/PlayPage")).PlayPage },
        },
        {
          path: "plays/:id/edit",
          lazy: {
            Component: async () => (await import("./features/plays/EditPlayPage")).EditPlayPage,
          },
        },
        {
          path: "accounts",
          lazy: {
            Component: async () => (await import("./features/accounts/AccountsPage")).AccountsPage,
          },
        },
        {
          path: "roles",
          lazy: { Component: async () => (await import("./features/roles/RolesPage")).RolesPage },
        },
        {
          path: "settings",
          lazy: {
            Component: async () => (await import("./features/settings/SettingsPage")).SettingsPage,
          },
        },
        { path: "*", element: <NotFoundPage /> },
      ],
    },
  ]
}

export const router = createBrowserRouter(appRoutes())
