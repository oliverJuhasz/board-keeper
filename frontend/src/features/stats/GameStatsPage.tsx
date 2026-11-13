import { useTranslation } from "react-i18next"
import { Link, useParams } from "react-router"
import { api, type Game, type GameModule, type GameStats } from "@/api/client"
import { problemCode } from "@/api/problems"
import { LinkButton } from "@/components/LinkButton"
import { PageHeader } from "@/components/PageHeader"
import { PageMessage } from "@/components/PageMessage"
import { QueryState } from "@/components/QueryState"
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
import { useCan } from "@/features/session/useSession"
import { formatRecentDay } from "@/lib/dates"
import { formatAverage, formatShare } from "@/lib/numbers"

/** What a table shows where there is no number, such as a best score when none was recorded. */
const NONE = "–"

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

  return (
    <div className="space-y-8">
      <PageHeader
        title={gameName(game)}
        description={
          stats.lastPlayedOn
            ? [
                t("stats.game.played", { count: stats.plays }),
                t("games.lastPlayed", { when: formatRecentDay(stats.lastPlayedOn) }),
              ].join(" · ")
            : t("games.neverPlayed")
        }
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
          {stats.players.length > 0 && <PlayersTable players={stats.players} />}
          {module && <PadAverages averages={stats.categoryAverages} module={module} />}
          {stats.cooperative && <Together group={stats.cooperative} />}
          {stats.solo.length > 0 && <SoloTable solo={stats.solo} />}
          <p>
            <Link
              to={`/plays?game=${game.id}`}
              className="text-sm underline underline-offset-4 hover:text-muted-foreground"
            >
              {t("stats.game.allPlays", { game: gameName(game) })}
            </Link>
          </p>
        </>
      )}
    </div>
  )
}

/**
 * How each Player did against the others. A phone has little room for the columns, so their
 * headings may wrap, and the win rate sits under the wins instead of in a column of its own.
 */
function PlayersTable({ players }: { players: GameStats["players"] }) {
  const { t } = useTranslation()
  // A Game recorded with only the winner has no scores and no Placings to show.
  const scored = players.some((player) => player.bestScore !== undefined)
  const placed = players.some((player) => player.averagePlacing !== undefined)

  return (
    <section className="space-y-2">
      <h2 className="text-sm font-medium">{t("stats.game.players")}</h2>
      <Table aria-label={t("stats.game.players")}>
        <TableHeader>
          <TableRow>
            <TableHead>{t("stats.game.player")}</TableHead>
            <TableHead className="text-right">{t("stats.game.wins")}</TableHead>
            {scored && <NumberHead>{t("stats.game.bestScore")}</NumberHead>}
            {scored && <NumberHead>{t("stats.game.averageScore")}</NumberHead>}
            {placed && <NumberHead>{t("stats.game.averagePlacing")}</NumberHead>}
          </TableRow>
        </TableHeader>
        <TableBody>
          {players.map((player) => (
            <TableRow key={player.playerId}>
              <TableCell className="font-medium whitespace-normal">{player.name}</TableCell>
              <TableCell className="text-right tabular-nums">
                {t("stats.game.winsOf", { wins: player.wins, plays: player.plays })}
                <span className="block text-xs text-muted-foreground">
                  {formatShare(player.wins / player.plays)}
                </span>
              </TableCell>
              {scored && <NumberCell value={player.bestScore} />}
              {scored && <NumberCell value={player.averageScore} />}
              {placed && <NumberCell value={player.averagePlacing} />}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </section>
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

  return (
    <section className="space-y-2">
      <h2 className="text-sm font-medium">{t("stats.game.together")}</h2>
      {/* A description list: each term with its value next to it. */}
      <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-1 text-muted-foreground">
        <dt>{t("stats.game.won")}</dt>
        <dd className="tabular-nums">
          {t("stats.game.winsOf", { wins: group.wins, plays: group.plays })} (
          {formatShare(group.wins / group.plays)})
        </dd>
        {group.bestScore !== undefined && (
          <>
            <dt>{t("stats.game.bestScore")}</dt>
            <dd className="tabular-nums">{group.bestScore}</dd>
          </>
        )}
        {group.averageScore !== undefined && (
          <>
            <dt>{t("stats.game.averageScore")}</dt>
            <dd className="tabular-nums">{formatAverage(group.averageScore)}</dd>
          </>
        )}
      </dl>
    </section>
  )
}

/**
 * The Solo Plays, which count nowhere else. They are won or lost only against an automated
 * opponent; played for a score only, they have no wins to show.
 */
function SoloTable({ solo }: { solo: GameStats["solo"] }) {
  const { t } = useTranslation()
  const againstOpponent = solo.some((player) => player.playsAgainstOpponent > 0)
  const scored = solo.some((player) => player.bestScore !== undefined)

  return (
    <section className="space-y-2">
      <h2 className="text-sm font-medium">{t("stats.game.solo")}</h2>
      <Table aria-label={t("stats.game.solo")}>
        <TableHeader>
          <TableRow>
            <TableHead>{t("stats.game.player")}</TableHead>
            <NumberHead>{t("stats.game.plays")}</NumberHead>
            {againstOpponent && <NumberHead>{t("stats.game.wins")}</NumberHead>}
            {scored && <NumberHead>{t("stats.game.bestScore")}</NumberHead>}
            {scored && <NumberHead>{t("stats.game.averageScore")}</NumberHead>}
          </TableRow>
        </TableHeader>
        <TableBody>
          {solo.map((player) => (
            <TableRow key={player.playerId}>
              <TableCell className="font-medium whitespace-normal">{player.name}</TableCell>
              <TableCell className="text-right tabular-nums">{player.plays}</TableCell>
              {againstOpponent && (
                <TableCell className="text-right tabular-nums">
                  {player.playsAgainstOpponent > 0
                    ? t("stats.game.winsOf", {
                        wins: player.wins,
                        plays: player.playsAgainstOpponent,
                      })
                    : NONE}
                </TableCell>
              )}
              {scored && <NumberCell value={player.bestScore} />}
              {scored && <NumberCell value={player.averageScore} />}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </section>
  )
}

/** The heading of a column of numbers. It may wrap, so that a phone has room for the columns. */
function NumberHead({ children }: { children: string }) {
  return <TableHead className="text-right whitespace-normal">{children}</TableHead>
}

/** A number in a table: whole numbers as they are, averages with at most one decimal. */
function NumberCell({ value }: { value?: number }) {
  return (
    <TableCell className="text-right text-muted-foreground tabular-nums">
      {value === undefined ? NONE : formatAverage(value)}
    </TableCell>
  )
}
