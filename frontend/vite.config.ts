import path from "node:path"
import tailwindcss from "@tailwindcss/vite"
import react from "@vitejs/plugin-react"
import { VitePWA } from "vite-plugin-pwa"
import { defineConfig } from "vitest/config"
import { thirdPartyLicenses } from "./vite-plugins/thirdPartyLicenses.ts"

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    // Makes Board Keeper installable as an app, on a phone's home screen for example. The plugin
    // writes two files into the build:
    // - manifest.webmanifest: the app's name, icons and colours, which a browser reads to offer
    //   "Install" or "Add to Home Screen";
    // - sw.js, a service worker: a script the browser runs apart from the page, which keeps the
    //   app's files on the device and serves them from there, so the app opens without waiting
    //   for the network. Workbox, Google's service worker library, does the work; the plugin
    //   hands it the list of files the build made.
    VitePWA({
      // A new version waits until the person chooses to reload (UpdateNotice), so a form they
      // are filling in is never lost.
      registerType: "prompt",
      // The app registers the service worker itself, through useRegisterSW in UpdateNotice.
      injectRegister: false,
      includeAssets: ["favicon.svg", "apple-touch-icon.png"],
      manifest: {
        name: "Board Keeper",
        short_name: "Board Keeper",
        description: "Record the board games you play, and see who wins.",
        start_url: "/",
        scope: "/",
        display: "standalone",
        background_color: "#ffffff",
        theme_color: "#262626",
        icons: [
          { src: "icon-192.png", sizes: "192x192", type: "image/png" },
          { src: "icon-512.png", sizes: "512x512", type: "image/png" },
          // A "maskable" icon fills the whole square and keeps what matters in the middle, so
          // Android can cut it into a circle or any other shape.
          {
            src: "icon-maskable-512.png",
            sizes: "512x512",
            type: "image/png",
            purpose: "maskable",
          },
        ],
      },
      workbox: {
        // Keep every file of the build on the device.
        globPatterns: ["**/*.{js,css,html,svg,png,webmanifest}"],
        // Any page address is the single-page app's index.html, which React Router reads...
        navigateFallback: "/index.html",
        // ...except the server's own addresses, which must always reach the server, and files,
        // such as third-party-licenses.txt: an address whose last part has a dot in it is a file,
        // as the server sees it too (FrontendResourceConfiguration). Workbox tests these against
        // the path and the query together, so the last pattern looks only before any "?".
        navigateFallbackDenylist: [
          /^\/api\//,
          /^\/actuator/,
          /^\/swagger-ui/,
          /^\/v3\//,
          /^[^?]*\.[^/?]*(\?|$)/,
        ],
        // Throw away the files of older versions once a new one is in use.
        cleanupOutdatedCaches: true,
      },
    }),
    // Lists the licences of the packages in the build, in third-party-licenses.txt. It finds the
    // packages the JavaScript imports by itself; the others are named here.
    thirdPartyLicenses([
      // Imported by src/index.css.
      "tailwindcss",
      "tw-animate-css",
      "shadcn",
      "@fontsource-variable/geist",
      // In the service worker, which vite-plugin-pwa builds apart.
      "workbox-core",
      "workbox-precaching",
      "workbox-routing",
      "workbox-strategies",
    ]),
  ],
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
