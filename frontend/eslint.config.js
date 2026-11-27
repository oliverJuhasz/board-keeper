import js from "@eslint/js"
import prettier from "eslint-config-prettier/flat"
import reactHooks from "eslint-plugin-react-hooks"
import reactRefresh from "eslint-plugin-react-refresh"
import { defineConfig, globalIgnores } from "eslint/config"
import globals from "globals"
import tseslint from "typescript-eslint"

export default defineConfig([
  globalIgnores(["dist", "src/api/schema.d.ts"]),
  {
    files: ["**/*.{ts,tsx}"],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      // Catches broken uses of React hooks, such as calling one inside an "if".
      reactHooks.configs.flat.recommended,
      // Keeps files in a shape that Vite can hot-reload while you edit.
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      ecmaVersion: 2023,
      globals: globals.browser,
    },
  },
  {
    // shadcn/ui components export their style variants next to the component itself.
    files: ["src/components/ui/**"],
    rules: { "react-refresh/only-export-components": "off" },
  },
  {
    // Tests find elements by test id only (ADR 0023), never by their role, text or label, which
    // change with the wording, the language or the kind of element. A test checks a text once it
    // has found its element, with toHaveTextContent or, for what a screen reader is told,
    // toHaveAccessibleName.
    files: ["src/**/*.test.{ts,tsx}", "src/test/**"],
    rules: {
      "no-restricted-syntax": [
        "error",
        {
          selector:
            "CallExpression[callee.property.name=/^(get|getAll|query|queryAll|find|findAll)By(Role|Text|LabelText|PlaceholderText|AltText|Title|DisplayValue)$/]",
          message: "Find elements by test id (ADR 0023).",
        },
        {
          selector:
            "CallExpression[callee.name=/^(get|getAll|query|queryAll|find|findAll)By(Role|Text|LabelText|PlaceholderText|AltText|Title|DisplayValue)$/]",
          message: "Find elements by test id (ADR 0023).",
        },
      ],
    },
  },
  // Last, so it can switch off every rule that would argue with Prettier about formatting.
  prettier,
])
