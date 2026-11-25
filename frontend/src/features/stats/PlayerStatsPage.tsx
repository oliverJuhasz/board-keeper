import { useTranslation } from "react-i18next"
import { Link, useParams } from "react-router"
import { api, type Game, type PlayerSummary } from "@/api/client"
import { problemCode } from "@/api/problems"
import { PageHeader } from "@/components/PageHeader"
import { PageMessage } from "@/components/PageMessage"
import { QueryState } from "@/components/QueryState"
import { QuietLink } from "@/components/QuietLink"
import { useGameName } from "@/features/games/useGameModules"
import { useGameModuleTextsQuery } from "@/features/games/useGameModuleTexts"
import { formatAverage } from "@/lib/numbers"
import { ResultsTable, SoloTable } from "./StatsTables"
import { usePlayedDescription } from "./usePlayedDescription"
import { useWinsOf } from "./useWinsOf"

/** The statistics of one Player, at /players/<id>. */
export function PlayerStatsPage() {
  const { t } = useTranslation()
  const { id = "" } = useParams()
  const stats = api.useQuery("get", "/api/stats/players/{playerId}", {
    params: { path: { playerId: id } },
  })
  // For the names of the Games, which a Game with a module of its own takes from the module.
  const games = api.useQuery("get", "/api/games")
  const texts = useGameModuleTextsQuery()

  if (problemCode(stats.error) === "players.notFound") {
    return (
      <PageMessage title={t("problems.players.notFound")}>
        <Link to="/players" className="text-foreground underline underline-offset-4">
          {t("stats.player.backToPlayers")}
        </Link>
      </PageMessage>
    )
  }
  return (
    <QueryState
      queries={[stats, games, texts]}
      loading={t("stats.player.loading")}
      failed={t("stats.player.loadFailed")}
    >
      {() => stats.data && <PlayerStatsDetails stats={stats.data} games={games.data ?? []} />}
    </QueryState>
  )
}

function PlayerStatsDetails({ stats, games }: { stats: PlayerSummary; games: Game[] }) {
  const { t } = useTranslation()
  const gameName = useGameName()
  const winsOf = useWinsOf()
  const playedDescription = usePlayedDescription("player")
  // A Game removed meanwhile has no name to show: its row is left out.
  const named = (gameId: string) => games.find((game) => game.id === gameId)
  const gameLink = (game: Game) => (
    <QuietLink to={`/games/${game.id}`} data-testid="stats-link">
      {gameName(game)}
    </QuietLink>
  )
  const rowsFor = <Row extends { gameId: string }>(rows: Row[]) =>
    rows.flatMap((row) => {
      const game = named(row.gameId)
      return game ? [{ ...row, key: game.id, label: gameLink(game) }] : []
    })

  return (
    <div className="space-y-8">
      <PageHeader
        title={stats.name}
        description={playedDescription(stats, t("stats.player.neverPlayed"))}
      />

      {stats.plays === 0 ? (
        <p className="text-muted-foreground" data-testid="no-stats">
          {t("stats.player.empty", { name: stats.name })}
        </p>
      ) : (
        <>
          {/* A description list: each term with its value next to it. Cooperative Plays and
              Solo Plays are counted apart from the Plays against others. */}
          <dl
            className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-1 text-muted-foreground"
            data-testid="player-summary"
          >
            {stats.againstOthers && (
              <>
                <dt>{t("stats.player.winsAgainstOthers")}</dt>
                <dd className="tabular-nums">
                  {winsOf(stats.againstOthers.wins, stats.againstOthers.plays)}
                </dd>
              </>
            )}
            {stats.againstOthers?.averagePlacing !== undefined && (
              <>
                <dt>{t("stats.columns.averagePlacing")}</dt>
                <dd className="tabular-nums">
                  {formatAverage(stats.againstOthers.averagePlacing)}
                </dd>
              </>
            )}
            {stats.together && (
              <>
                <dt>{t("stats.player.winsTogether")}</dt>
                <dd className="tabular-nums">
                  {winsOf(stats.together.wins, stats.together.plays)}
                </dd>
              </>
            )}
            {stats.soloPlays > 0 && (
              <>
                <dt>{t("stats.solo")}</dt>
                <dd className="tabular-nums">{stats.soloPlays}</dd>
              </>
            )}
          </dl>
          {stats.games.length > 0 && (
            <ResultsTable
              title={t("stats.player.games")}
              labelHeading={t("stats.columns.game")}
              rows={rowsFor(stats.games)}
            />
          )}
          {stats.solo.length > 0 && (
            <SoloTable labelHeading={t("stats.columns.game")} rows={rowsFor(stats.solo)} />
          )}
          <p>
            <QuietLink
              to={`/plays?player=${stats.playerId}`}
              className="text-sm"
              data-testid="all-plays"
            >
              {t("stats.player.allPlays", { name: stats.name })}
            </QuietLink>
          </p>
        </>
      )}
    </div>
  )
}
