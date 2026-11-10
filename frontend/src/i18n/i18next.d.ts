import "i18next"
import type en from "./locales/en.json"

// Tells TypeScript which texts exist, taken from the English file. A misspelt name in
// t("games.titel") is then a compile error instead of a blank on the page.
declare module "i18next" {
  interface CustomTypeOptions {
    defaultNS: "translation"
    resources: {
      translation: typeof en
    }
  }
}
