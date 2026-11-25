import { RotateCcwIcon } from "lucide-react"
import { useTranslation } from "react-i18next"
import { Link } from "react-router"
import { api, type Game, type Play } from "@/api/client"
import { useGameName } from "@/features/games/useGameModules"
import { useCurrentAccount } from "@/features/session/useSession"
import { formatRecentDay } from "@/lib/dates"

/** How many recent Plays are offered to play again. */
const OFFERED = 3

/**
 * The quickest way to record a Play: the last few Plays the signed-in Player took part in, each
 * a link to the form with the same Game, Players and choices already filled in. A Play with the
 * same Game and Players as a more recent one is offered only once.
 *
 * @param games the Games in the catalogue; a Play of a Game that is no longer there is left out
 */
export function PlayAgain({ games }: { games: Game[] }) {
  const { t } = useTranslation()
  const gameName = useGameName()
  const account = useCurrentAccount()
  const recent = api.useQuery("get", "/api/plays", {
    params: { query: { playerId: [account.playerId], limit: 10 } },
  })

  const seen = new Set<string>()
  const offers = (recent.data?.plays ?? [])
    .filter((play) => {
      if (!games.some((game) => game.id === play.game.id)) {
        return false
      }
      const together = `${play.game.id}:${playersOf(play)
        .map((player) => player.id)
        .toSorted()
        .join(",")}`
      if (seen.has(together)) {
        return false
      }
      seen.add(together)
      return true
    })
    .slice(0, OFFERED)

  // Nothing to offer, or not loaded yet: the Games below are the way to start.
  if (offers.length === 0) {
    return null
  }
  return (
    <section className="space-y-2" data-testid="play-again">
      <h2 className="text-sm font-medium">{t("games.playAgain")}</h2>
      <ul className="divide-y rounded-lg border">
        {offers.map((play) => {
          const names = playersOf(play)
            .map((player) => player.name)
            .join(", ")
          return (
            <li key={play.id}>
              <Link
                to={`/plays/new?game=${play.game.id}&again=${play.id}`}
                aria-label={t("games.playAgainWith", { game: gameName(play.game), players: names })}
                className="flex items-center gap-3 px-3 py-2.5 hover:bg-muted/50"
                data-testid="play-again-offer"
              >
                <RotateCcwIcon aria-hidden className="size-4 shrink-0 text-muted-foreground" />
                <span className="min-w-0 flex-1">
                  <span className="block font-medium">{gameName(play.game)}</span>
                  <span className="block truncate text-sm text-muted-foreground">{names}</span>
                </span>
                <span className="shrink-0 text-sm text-muted-foreground">
                  {formatRecentDay(play.playedOn)}
                </span>
              </Link>
            </li>
          )
        })}
      </ul>
    </section>
  )
}

/** A Play's Players, in the order they were entered. */
function playersOf(play: Play) {
  return play.results.map((result) => result.player)
}
