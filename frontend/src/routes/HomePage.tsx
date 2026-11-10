import { GameModulesCard } from "@/features/games/GameModulesCard"
import { GamesCard } from "@/features/games/GamesCard"

export function HomePage() {
  return (
    <div className="grid gap-6 sm:grid-cols-2">
      <GamesCard />
      <GameModulesCard />
    </div>
  )
}
