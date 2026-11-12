import { useTranslation } from "react-i18next"
import { api } from "@/api/client"
import { currentLanguage } from "@/i18n"

/**
 * The texts that the installed Game Modules bring with them, in the current language, by name:
 * `texts["generic.invalid.winnerMissing"]`. Empty until they have loaded.
 *
 * These texts live in the backend, next to each module's code, so the frontend fetches them
 * instead of having them in its own translation files.
 */
export function useGameModuleTexts(): Record<string, string> {
  // Subscribes to language changes, so the texts are fetched again in the new language.
  useTranslation()
  const texts = api.useQuery(
    "get",
    "/api/game-modules/texts/{language}",
    { params: { path: { language: currentLanguage() } } },
    // They only change when the application is updated.
    { staleTime: Infinity },
  )
  return texts.data ?? {}
}
