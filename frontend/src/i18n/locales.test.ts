import { expect, test } from "vitest"
import en from "./locales/en.json"
import hu from "./locales/hu.json"
import nl from "./locales/nl.json"

/** Every text's full name, such as "games.title", in a nested object of texts. */
function names(texts: object, prefix = ""): string[] {
  return Object.entries(texts)
    .flatMap(([key, value]) =>
      typeof value === "string" ? [prefix + key] : names(value, `${prefix}${key}.`),
    )
    .sort()
}

// A text added in English but forgotten in another language would quietly show up in English.
test.each([
  ["Dutch", nl],
  ["Hungarian", hu],
])("%s has exactly the same texts as English", (_language, texts) => {
  expect(names(texts)).toEqual(names(en))
})

test.each([
  ["English", en],
  ["Dutch", nl],
  ["Hungarian", hu],
])("no %s text is empty", (_language, texts) => {
  const flat = JSON.stringify(texts)
  expect(flat).not.toContain('""')
})
