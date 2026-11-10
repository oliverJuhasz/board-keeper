import { Outlet } from "react-router"
import { LanguageSelect } from "@/i18n/LanguageSelect"

/** The frame around the pages shown without signing in: one narrow column. */
export function PublicLayout() {
  return (
    <div className="mx-auto flex min-h-svh max-w-sm flex-col gap-8 px-4 py-10">
      <header className="flex items-center justify-between">
        <span className="font-heading text-lg font-semibold">Board Keeper</span>
        <LanguageSelect />
      </header>
      <main>
        <Outlet />
      </main>
    </div>
  )
}
