import { useTranslation } from "react-i18next"
import { Link, useParams } from "react-router"
import { api, type Game, type GameModule, type GameStats } from "@/api/client"
import { problemCode } from "@/api/problems"
import { LinkButton } from "@/components/LinkButton"
import { PageHeader } from "@/components/PageHeader"
import { PageMessage } from "@/components/PageMessage"
import { QueryState } from "@/components/QueryState"
import { QuietLink } from "@/components/QuietLink"
import { Table, TableBody, TableHead, TableRow } from "@/components/ui/table"
import { moduleWithId, useGameModules, useGameName } from "@/features/games/useGameModules"
import { useGameModuleTexts, useGameModuleTextsQuery } from "@/features/games/useGameModuleTexts"
import { useCan } from "@/features/session/useSession"
import { formatAverage } from "@/lib/numbers"
import { NumberCell, ResultsTable, SoloTable } from "./StatsTables"
import { usePlayedDescription } from "./usePlayedDescription"
import { useWinsOf } from "./useWinsOf"

/** The statistics of one Game, at /games/<id>. */
export function GameStatsPage() {
  const { t } = useTranslation()
  const { id = "" } = useParams()
  const stats = api.useQuery("get", "/api/stats/games/{gameId}", {
    params: { path: { gameId: id } },
  })
  const games = api.useQuery("get", "/api/games")
  const modules = useGameModules()
  const texts = useGameModuleTextsQuery()

  if (problemCode(stats.error) === "games.notFound") {
    return (
      <PageMessage title={t("problems.games.notFound")}>
        <Link to="/" className="text-foreground underline underline-offset-4">
          {t("stats.game.backToGames")}
        </Link>
      </PageMessage>
    )
  }
  return (
    <QueryState
      queries={[stats, games, modules, texts]}
      loading={t("stats.game.loading")}
      failed={t("stats.game.loadFailed")}
    >
      {() => {
        const game = games.data?.find((candidate) => candidate.id === id)
        return (
          stats.data &&
          game && (
            <GameStatsDetails
              stats={stats.data}
              game={game}
              module={moduleWithId(modules.data, stats.data.gameModuleId)}
            />
          )
        )
      }}
    </QueryState>
  )
}

/**
 * @param module the Game Module that scores the Game, for the names of its Score Categories.
 *     Undefined when it is not part of this build: the averages per category are left out.
 */
function GameStatsDetails({
  stats,
  game,
  module,
}: {
  stats: GameStats
  game: Game
  module?: GameModule
}) {
  const { t } = useTranslation()
  const gameName = useGameName()
  const can = useCan()
  const playedDescription = usePlayedDescription("game")
  const playerLink = (playerId: string, name: string) => (
    <QuietLink to={`/players/${playerId}`}>{name}</QuietLink>
  )

  return (
    <div className="space-y-8">
      <PageHeader
        title={gameName(game)}
        description={playedDescription(stats, t("games.neverPlayed"))}
        action={
          can("CREATE_PLAYS") && (
            <LinkButton to={`/plays/new?game=${game.id}`}>{t("stats.game.record")}</LinkButton>
          )
        }
      />

      {stats.plays === 0 ? (
        <p className="text-muted-foreground">{t("stats.game.empty")}</p>
      ) : (
        <>
          {stats.players.length > 0 && (
            <ResultsTable
              title={t("stats.game.players")}
              labelHeading={t("stats.columns.player")}
              rows={stats.players.map((player) => ({
                ...player,
                key: player.playerId,
                label: playerLink(player.playerId, player.name),
              }))}
            />
          )}
          {module && <PadAverages averages={stats.categoryAverages} module={module} />}
          {stats.cooperative && <Together group={stats.cooperative} />}
          {stats.solo.length > 0 && (
            <SoloTable
              labelHeading={t("stats.columns.player")}
              rows={stats.solo.map((player) => ({
                ...player,
                key: player.playerId,
                label: playerLink(player.playerId, player.name),
              }))}
            />
          )}
          <p>
            <QuietLink to={`/plays?game=${game.id}`} className="text-sm">
              {t("stats.game.allPlays", { game: gameName(game) })}
            </QuietLink>
          </p>
        </>
      )}
    </div>
  )
}

/** The average of each row of the score pad, in the order of the pad. */
function PadAverages({
  averages,
  module,
}: {
  averages: GameStats["categoryAverages"]
  module: GameModule
}) {
  const { t } = useTranslation()
  const texts = useGameModuleTexts()
  const rows = module.scorePad.categories.filter((category) => averages[category.key] !== undefined)

  // With one row, the average score says it all.
  if (rows.length < 2) {
    return null
  }
  return (
    <section className="space-y-2">
      <div className="space-y-1">
        <h2 className="text-sm font-medium">{t("stats.game.padAverages")}</h2>
        <p className="text-sm text-muted-foreground">{t("stats.game.padAveragesHint")}</p>
      </div>
      <Table aria-label={t("stats.game.padAverages")}>
        <TableBody>
          {rows.map((category) => (
            <TableRow key={category.key}>
              <TableHead scope="row" className="font-normal whitespace-normal">
                {texts[`${module.key}.category.${category.key}`]}
              </TableHead>
              <NumberCell value={averages[category.key]} />
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </section>
  )
}

/** How the group did in the Cooperative Plays. */
function Together({ group }: { group: NonNullable<GameStats["cooperative"]> }) {
  const { t } = useTranslation()
  const winsOf = useWinsOf()

  return (
    <section className="space-y-2">
      <h2 className="text-sm font-medium">{t("stats.together")}</h2>
      {/* A description list: each term with its value next to it. */}
      <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-1 text-muted-foreground">
        <dt>{t("stats.won")}</dt>
        <dd className="tabular-nums">{winsOf(group.wins, group.plays)}</dd>
        {group.bestScore !== undefined && (
          <>
            <dt>{t("stats.columns.bestScore")}</dt>
            <dd className="tabular-nums">{group.bestScore}</dd>
          </>
        )}
        {group.averageScore !== undefined && (
          <>
            <dt>{t("stats.columns.averageScore")}</dt>
            <dd className="tabular-nums">{formatAverage(group.averageScore)}</dd>
          </>
        )}
      </dl>
    </section>
  )
}
