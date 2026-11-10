import { Link, Outlet } from "react-router"

/** The frame around every page. `<Outlet />` is where the current page is drawn. */
export function AppLayout() {
  return (
    <div className="min-h-svh">
      <header className="border-b">
        <div className="mx-auto flex h-14 max-w-3xl items-center px-4">
          <Link to="/" className="font-heading text-lg font-semibold">
            Board Keeper
          </Link>
        </div>
      </header>
      <main className="mx-auto max-w-3xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  )
}
