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

/** A date with its time, such as "17 November 2026 at 14:30". */
export function formatDateTime(isoDate: string) {
  return new Intl.DateTimeFormat(locales[currentLanguage()], {
    dateStyle: "long",
    timeStyle: "short",
  }).format(new Date(isoDate))
}

/**
 * A day without a time, such as the "2026-03-14" a Play was played on, written out. Unlike
 * formatDate, it shows the same day in every time zone: a day on its own is not a moment.
 */
export function formatDay(isoDay: string) {
  return new Intl.DateTimeFormat(locales[currentLanguage()], {
    dateStyle: "long",
    timeZone: "UTC",
  }).format(new Date(isoDay))
}

/** Today, where the person is, in the form a date input and the API use: "2026-03-14". */
export function todayIso() {
  const now = new Date()
  const month = String(now.getMonth() + 1).padStart(2, "0")
  const day = String(now.getDate()).padStart(2, "0")
  return `${now.getFullYear()}-${month}-${day}`
}
