import { api, type GameModule } from "@/api/client"
import { useGameModuleTexts } from "./useGameModuleTexts"

/** The key of the Generic Module, which scores every Game that has no Game Module of its own. */
export const GENERIC = "generic"

/**
 * The installed Game Modules, each with its score pad: the description a form for a Play is
 * drawn from. Pass the result to QueryState like any other query.
 */
export function useGameModules() {
  // They only change when the application is updated.
  return api.useQuery("get", "/api/game-modules", {}, { staleTime: Infinity })
}

/** Finds the module with this id among the installed ones. */
export function moduleWithId(modules: GameModule[] | undefined, id: string) {
  return modules?.find((module) => module.id === id)
}

/**
 * Gives a function that says what to call a Game. A Game with a Game Module of its own is
 * named by the module, in the current language; any other Game has the name someone typed.
 */
export function useGameName() {
  const texts = useGameModuleTexts()

  return (game: { name: string; moduleKey?: string }) =>
    (game.moduleKey && game.moduleKey !== GENERIC && texts[`${game.moduleKey}.name`]) || game.name
}
