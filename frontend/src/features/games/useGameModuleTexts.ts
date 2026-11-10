import { keepPreviousData } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { api } from "@/api/client"
import { currentLanguage } from "@/i18n"

/**
 * The texts of the installed Game Modules in the current language. They live in the backend,
 * inside each Game Module, so they are loaded from the API instead of the frontend's own files.
 *
 * Returns a function that gives the text for a name such as "generic.name". Until the texts
 * have arrived, or if they can't be loaded, it gives the fallback instead.
 */
export function useGameModuleTexts() {
  // Subscribes this hook to language changes, so the texts are loaded again in the new language.
  useTranslation()

  const texts = api.useQuery(
    "get",
    "/api/game-modules/texts/{language}",
    { params: { path: { language: currentLanguage() } } },
    {
      // They only change when the application is updated, so never reload them in the background.
      staleTime: Infinity,
      // While another language's texts are on their way, keep showing the ones we have.
      placeholderData: keepPreviousData,
    },
  )

  return (name: string, fallback: string) => texts.data?.[name] ?? fallback
}
