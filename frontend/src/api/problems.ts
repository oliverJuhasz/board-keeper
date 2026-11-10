import type { TFunction } from "i18next"
import i18n from "@/i18n"
import type { Problem } from "./client"

/** The code of a failed API call, such as "players.nameTaken", if the server gave one. */
export function problemCode(error: unknown): string | undefined {
  const code = (error as Problem | null | undefined)?.code
  return typeof code === "string" ? code : undefined
}

/**
 * The message to show for a failed API call, in the current language. The server sends only a
 * code; the texts are under "problems" in the translation files.
 */
export function problemMessage(t: TFunction, error: unknown): string {
  const code = problemCode(error)
  if (code && i18n.exists(`problems.${code}`)) {
    return t(`problems.${code}` as "problems.unknown")
  }
  // No answer at all: fetch() fails with a TypeError when the server can't be reached.
  return error instanceof TypeError ? t("problems.network") : t("problems.unknown")
}
