import { currentLanguage, type LanguageCode } from "@/i18n"

// Which regional conventions each language's dates follow. English uses the British ones
// (17 November 2026), which put the day first, as Dutch and Hungarian readers expect.
const locales: Record<LanguageCode, string> = { en: "en-GB", nl: "nl-NL", hu: "hu-HU" }

/** A date written out the way the current language does, such as "17 November 2026". */
export function formatDate(isoDate: string) {
  return new Intl.DateTimeFormat(locales[currentLanguage()], { dateStyle: "long" }).format(
    new Date(isoDate),
  )
}
