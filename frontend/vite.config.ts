import path from "node:path"
import tailwindcss from "@tailwindcss/vite"
import react from "@vitejs/plugin-react"
import { defineConfig } from "vitest/config"

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    // "@/..." means "src/...". Keep in step with "paths" in tsconfig.app.json.
    alias: {
      "@": path.resolve(import.meta.dirname, "./src"),
    },
  },
  server: {
    // In development the frontend runs on Vite's own server. Calls to the API are passed
    // on to Spring Boot, so the browser sees one address, as it does in production.
    proxy: {
      "/api": "http://localhost:8080",
    },
  },
  test: {
    // jsdom gives tests a simulated browser page to render components into.
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
  },
})
