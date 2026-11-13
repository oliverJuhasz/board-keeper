import { currentLanguage, type LanguageCode } from "@/i18n"

// Which regional conventions each language's dates and numbers follow. English uses the British
// ones (17 November 2026), which put the day first, as Dutch and Hungarian readers expect.
const locales: Record<LanguageCode, string> = { en: "en-GB", nl: "nl-NL", hu: "hu-HU" }

/** The locale that the Intl formatters get for the current language. */
export function formattingLocale() {
  return locales[currentLanguage()]
}
