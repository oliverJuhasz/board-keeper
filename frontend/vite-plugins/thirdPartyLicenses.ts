import fs from "node:fs"
import path from "node:path"
import type { Plugin } from "vite"

/**
 * Writes third-party-licenses.txt into the build: the licence of every package whose code ends
 * up in the files the browser downloads. Those licences (MIT, ISC, Apache 2.0 and the like) ask
 * that their notice goes along with every copy, and the minified files no longer carry the
 * comments that held it (ADR 0016).
 *
 * A Vite plugin is an object with functions ("hooks") that Vite calls during the build.
 * `generateBundle` runs once the output files are worked out, and tells which modules went into
 * each of them, so the list holds exactly the packages that were bundled.
 */
/** @param alsoIncluded packages in the build that the JavaScript doesn't import */
export function thirdPartyLicenses(alsoIncluded: string[]): Plugin {
  return {
    name: "board-keeper:third-party-licenses",
    apply: "build",
    generateBundle(_options, bundle) {
      const directories = new Set<string>()
      for (const output of Object.values(bundle)) {
        if (output.type === "chunk") {
          for (const id of output.moduleIds) {
            const directory = packageDirectoryOf(id)
            if (directory) {
              directories.add(directory)
            }
          }
        }
      }
      // Packages that reach the browser some other way, such as through the stylesheets.
      for (const name of alsoIncluded) {
        directories.add(path.resolve(import.meta.dirname, "../node_modules", name))
      }
      this.emitFile({
        type: "asset",
        fileName: "third-party-licenses.txt",
        source: listing([...directories].map(describe)),
      })
    },
  }
}

interface Package {
  name: string
  licence: string
  texts: string[]
}

/**
 * The folder of the package a module belongs to, or nothing for Board Keeper's own code. A
 * module's id is usually its file; some are virtual, made up by a tool and marked with "\0".
 */
function packageDirectoryOf(id: string): string | undefined {
  const file = id.replace(/^\0/, "").split("?")[0]
  if (file.startsWith("vite/")) {
    return path.resolve(import.meta.dirname, "../node_modules/vite")
  }
  if (file.startsWith("rolldown/")) {
    return path.resolve(import.meta.dirname, "../node_modules/rolldown")
  }
  const marker = `${path.sep}node_modules${path.sep}`
  const at = file.lastIndexOf(marker)
  if (at < 0) {
    return undefined
  }
  const parts = file.slice(at + marker.length).split(path.sep)
  const name = parts[0].startsWith("@") ? parts.slice(0, 2) : parts.slice(0, 1)
  return path.join(file.slice(0, at), "node_modules", ...name)
}

function describe(directory: string): Package {
  const manifest = JSON.parse(fs.readFileSync(path.join(directory, "package.json"), "utf8")) as {
    name: string
    license?: string | { type: string }
  }
  const licence =
    typeof manifest.license === "object" ? manifest.license.type : (manifest.license ?? "unknown")
  const texts = fs
    .readdirSync(directory)
    .filter((file) => /^(licen[cs]e|copying|notice)([.-].*)?$/i.test(file))
    .sort()
    .map((file) => fs.readFileSync(path.join(directory, file), "utf8").trim())
  return { name: manifest.name, licence, texts }
}

function listing(packages: Package[]): string {
  const sections = packages
    .sort((a, b) => a.name.localeCompare(b.name))
    .map((found) => {
      const texts =
        found.texts.length > 0
          ? found.texts.join("\n\n")
          : `The package names its licence as ${found.licence} and has no file with its text.`
      return `${"=".repeat(78)}\n${found.name} (${found.licence})\n${"=".repeat(78)}\n\n${texts}\n`
    })
  return [
    "Board Keeper is free software under the GNU Affero General Public License, version 3 or",
    "later. Its web app includes the following packages, each under its own licence.",
    "",
    // Two copies of a package with the same licence need it only once; a Set keeps one of each.
    ...new Set(sections),
  ].join("\n")
}
