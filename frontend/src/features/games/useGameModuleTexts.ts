import { useTranslation } from "react-i18next"
import { api } from "@/api/client"
import { currentLanguage } from "@/i18n"

/**
 * Fetches the texts that the installed Game Modules bring with them, in the current language.
 * A page that draws a module's score pad passes this to QueryState, so that it waits for the
 * texts and never shows a bare key.
 *
 * These texts live in the backend, next to each module's code, so the frontend fetches them
 * instead of having them in its own translation files.
 */
export function useGameModuleTextsQuery() {
  // Subscribes to language changes, so the texts are fetched again in the new language.
  useTranslation()
  return api.useQuery(
    "get",
    "/api/game-modules/texts/{language}",
    { params: { path: { language: currentLanguage() } } },
    // They only change when the application is updated.
    { staleTime: Infinity },
  )
}

/** The Game Modules' texts by name: `texts["calico.category.cats"]`. Empty until they have loaded. */
export function useGameModuleTexts(): Record<string, string> {
  return useGameModuleTextsQuery().data ?? {}
}
