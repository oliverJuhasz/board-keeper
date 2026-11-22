import js from "@eslint/js"
import prettier from "eslint-config-prettier/flat"
import playwright from "eslint-plugin-playwright"
import { defineConfig, globalIgnores } from "eslint/config"
import globals from "globals"
import tseslint from "typescript-eslint"

export default defineConfig([
  globalIgnores(["playwright-report", "test-results", "support/schema.d.ts"]),
  {
    files: ["**/*.ts"],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      // Catches common mistakes in Playwright tests, such as a forgotten "await" or a fixed
      // wait where an assertion that retries would do.
      playwright.configs["flat/recommended"],
    ],
    languageOptions: {
      ecmaVersion: 2023,
      globals: globals.node,
    },
    rules: {
      // Tests find elements by test id only (ADR 0023), never by their role, text or label,
      // which change with the wording, the language or the kind of element.
      "no-restricted-syntax": [
        "error",
        {
          selector:
            "CallExpression[callee.property.name=/^getBy(Role|Text|Label|Placeholder|AltText|Title)$/]",
          message: "Find elements with getByTestId (ADR 0023).",
        },
      ],
      // Nor by CSS selectors, which change with the markup. The page's <html> element is the
      // only exception: it says which language the page is in.
      "playwright/no-raw-locators": ["error", { allowed: ["html"] }],
      // A test must check something. A helper whose name starts with "expect", such as
      // expectAccessible, counts as a check.
      "playwright/expect-expect": ["warn", { assertFunctionPatterns: ["^expect[A-Z]"] }],
    },
  },
  // Last, so it can switch off every rule that would argue with Prettier about formatting.
  prettier,
])
