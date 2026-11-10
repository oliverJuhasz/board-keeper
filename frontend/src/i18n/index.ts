import i18n from "i18next"
import LanguageDetector from "i18next-browser-languagedetector"
import { initReactI18next } from "react-i18next"
import type { operations } from "@/api/schema"
import en from "./locales/en.json"
import hu from "./locales/hu.json"
import nl from "./locales/nl.json"

/** A language code, such as "hu". The type comes from the API, so the two can't disagree. */
export type LanguageCode = operations["getGameModuleTexts"]["parameters"]["path"]["language"]

/** The languages on offer, each under its own name, which is never translated. */
export const languages: { code: LanguageCode; name: string }[] = [
  { code: "en", name: "English" },
  { code: "nl", name: "Nederlands" },
  { code: "hu", name: "Magyar" },
]

const STORAGE_KEY = "boardkeeper.language"

void i18n
  // Works out the starting language: the one chosen earlier on this device, else the browser's.
  .use(LanguageDetector)
  // Connects i18next to React, so components redraw when the language changes.
  .use(initReactI18next)
  .init({
    resources: {
      en: { translation: en },
      nl: { translation: nl },
      hu: { translation: hu },
    },
    supportedLngs: languages.map((language) => language.code),
    fallbackLng: "en",
    detection: {
      order: ["localStorage", "navigator"],
      lookupLocalStorage: STORAGE_KEY,
      // "nl-BE" and "hu-HU" count as "nl" and "hu".
      convertDetectedLanguage: (detected) => detected.split("-")[0],
      // Don't store what was only detected; setLanguage() stores an explicit choice.
      caches: [],
    },
    // React already protects against HTML in texts, so i18next doesn't need to.
    interpolation: { escapeValue: false },
  })

// Tell the browser, screen readers and spell checkers which language the page is in.
document.documentElement.lang = currentLanguage()
i18n.on("languageChanged", () => {
  document.documentElement.lang = currentLanguage()
})

/** The language the interface is shown in right now. */
export function currentLanguage(): LanguageCode {
  const resolved = i18n.resolvedLanguage
  return languages.find((language) => language.code === resolved)?.code ?? "en"
}

/** Switches the interface to a language and remembers the choice on this device. */
export function setLanguage(code: LanguageCode) {
  localStorage.setItem(STORAGE_KEY, code)
  return i18n.changeLanguage(code)
}

export default i18n
