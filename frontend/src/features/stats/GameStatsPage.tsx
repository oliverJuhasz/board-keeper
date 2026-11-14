import { useTranslation } from "react-i18next"
import { Link, useParams } from "react-router"
import { useQueryClient } from "@tanstack/react-query"
import {
  api,
  type Game,
  type GameModule,
  type GameRecalculation,
  type GameStats,
} from "@/api/client"
import { explainProblem, problemCode } from "@/api/problems"
import { ActionsMenu } from "@/components/ActionsMenu"
import { FormProblem } from "@/components/FormProblem"
import { LinkButton } from "@/components/LinkButton"
import { PageHeader } from "@/components/PageHeader"
import { PageMessage } from "@/components/PageMessage"
import { QueryState } from "@/components/QueryState"
import { QuietLink } from "@/components/QuietLink"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { DropdownMenuItem } from "@/components/ui/dropdown-menu"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { moduleWithId, useGameModules, useGameName } from "@/features/games/useGameModules"
import { useGameModuleTexts, useGameModuleTextsQuery } from "@/features/games/useGameModuleTexts"
import { playsKey } from "@/features/plays/playQueries"
import { useCan } from "@/features/session/useSession"
import { formatDay } from "@/lib/dates"
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
  const queryClient = useQueryClient()
  const recalculate = api.useMutation("post", "/api/plays/recalculation", {
    onSuccess: () => {
      // The statistics and the history may still show the Results from before. The statistics
      // catch up a moment after the Plays change.
      queryClient.removeQueries({ queryKey: playsKey })
      void queryClient.invalidateQueries({
        predicate: (query) => String(query.queryKey[1]).startsWith("/api/stats"),
      })
    },
  })
  const playerLink = (playerId: string, name: string) => (
    <QuietLink to={`/players/${playerId}`}>{name}</QuietLink>
  )

  return (
    <div className="space-y-8">
      <PageHeader
        title={gameName(game)}
        description={playedDescription(stats, t("games.neverPlayed"))}
        action={
          <div className="flex items-center gap-1">
            {can("CREATE_PLAYS") && (
              <LinkButton to={`/plays/new?game=${game.id}`}>{t("stats.game.record")}</LinkButton>
            )}
            {/* For Admins only: running the Game Module over every Play of the Game again. */}
            {can("RECALCULATE_PLAYS") && (
              <ActionsMenu label={t("stats.game.actions")}>
                <DropdownMenuItem
                  onClick={() => recalculate.mutate({ params: { query: { gameId: game.id } } })}
                >
                  {t("stats.game.recalculate")}
                </DropdownMenuItem>
              </ActionsMenu>
            )}
          </div>
        }
      />
      {recalculate.data && <RecalculationReport report={recalculate.data} />}
      {recalculate.isError && <FormProblem error={recalculate.error} />}

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
          {module && stats.victoryTypes.length > 0 && (
            <VictoryTypes counts={stats.victoryTypes} module={module} />
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

/** How many Plays were won in each way, the most first, for a Game that can be won in several. */
function VictoryTypes({
  counts,
  module,
}: {
  counts: GameStats["victoryTypes"]
  module: GameModule
}) {
  const { t } = useTranslation()
  const texts = useGameModuleTexts()

  return (
    <section className="space-y-2">
      <h2 className="text-sm font-medium">{t("stats.game.victoryTypes")}</h2>
      <Table aria-label={t("stats.game.victoryTypes")}>
        <TableHeader>
          <TableRow>
            <TableHead>{t("stats.columns.victoryType")}</TableHead>
            <TableHead className="text-right">{t("stats.columns.plays")}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {counts.map((count) => (
            <TableRow key={count.key}>
              <TableHead scope="row" className="font-normal whitespace-normal">
                {texts[`${module.key}.victoryType.${count.key}`]}
              </TableHead>
              <TableCell className="text-right tabular-nums">{count.plays}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </section>
  )
}

/**
 * What recalculating every Play of the Game did: how many Plays got new Results, and which the
 * Game Module refuses now, with why. Those kept the Results they had.
 */
function RecalculationReport({ report }: { report: GameRecalculation }) {
  const { t } = useTranslation()
  const texts = useGameModuleTexts()

  return (
    <Alert>
      <AlertDescription className="space-y-2">
        <p>
          {t("stats.game.recalculated", { count: report.plays })}{" "}
          {report.changed === 0
            ? t("stats.game.nothingChanged")
            : t("stats.game.changed", { count: report.changed })}
        </p>
        {report.refused.length > 0 && (
          <>
            <p>{t("stats.game.refused", { count: report.refused.length })}</p>
            <ul className="list-disc pl-5">
              {report.refused.map((refused) => (
                <li key={refused.playId}>
                  <QuietLink to={`/plays/${refused.playId}`}>
                    {formatDay(refused.playedOn)}
                  </QuietLink>
                  {": "}
                  {/* The reason is the name of a text: a Game Module's own, one of the
                      frontend's, or a problem's code. */}
                  {explainProblem(t, { code: refused.reason, reason: refused.reason }, texts)}
                </li>
              ))}
            </ul>
          </>
        )}
      </AlertDescription>
    </Alert>
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
