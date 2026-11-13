import { formattingLocale } from "./locale"

/** A date written out the way the current language does, such as "17 November 2026". */
export function formatDate(isoDate: string) {
  return new Intl.DateTimeFormat(formattingLocale(), { dateStyle: "long" }).format(
    new Date(isoDate),
  )
}

/** A date with its time, such as "17 November 2026 at 14:30". */
export function formatDateTime(isoDate: string) {
  return new Intl.DateTimeFormat(formattingLocale(), {
    dateStyle: "long",
    timeStyle: "short",
  }).format(new Date(isoDate))
}

/**
 * A day without a time, such as the "2026-03-14" a Play was played on, written out. Unlike
 * formatDate, it shows the same day in every time zone: a day on its own is not a moment.
 */
export function formatDay(isoDay: string) {
  return new Intl.DateTimeFormat(formattingLocale(), {
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

/**
 * A day said the way people say it when it is recent: "today", "yesterday", "3 days ago". A day
 * longer ago is written out, as formatDay does.
 */
export function formatRecentDay(isoDay: string) {
  // Both days as midnight UTC, so the difference is a whole number of days wherever one is.
  const daysAgo = Math.round(
    (Date.parse(`${todayIso()}T00:00:00Z`) - Date.parse(`${isoDay}T00:00:00Z`)) / 86_400_000,
  )
  if (daysAgo >= 0 && daysAgo < 7) {
    // "auto" says "yesterday" instead of "1 day ago", and does so in every language.
    return new Intl.RelativeTimeFormat(formattingLocale(), { numeric: "auto" }).format(
      -daysAgo,
      "day",
    )
  }
  return formatDay(isoDay)
}
