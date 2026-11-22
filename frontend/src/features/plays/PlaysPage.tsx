import { TrophyIcon } from "lucide-react"
import { useId, type ReactNode } from "react"
import { useTranslation } from "react-i18next"
import { Link, useSearchParams } from "react-router"
import { api, type Play } from "@/api/client"
import { CheckboxField } from "@/components/CheckboxField"
import { PageHeader } from "@/components/PageHeader"
import { QueryState } from "@/components/QueryState"
import { Button } from "@/components/ui/button"
import { Field, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select"
import { useGameName } from "@/features/games/useGameModules"
import { useGameModuleTexts } from "@/features/games/useGameModuleTexts"
import { useCurrentAccount } from "@/features/session/useSession"
import { formatDay } from "@/lib/dates"

/**
 * The play history: every Play, newest first, narrowed by the filters.
 *
 * The filters live in the address (/plays?game=…&mine=1), not in the component. So a filtered
 * list can be bookmarked, and the browser's back button returns to it as it was.
 */
export function PlaysPage() {
  const { t } = useTranslation()
  const account = useCurrentAccount()
  const gameName = useGameName()
  const [searchParams, setSearchParams] = useSearchParams()
  const filters = {
    mine: searchParams.get("mine") === "1",
    game: searchParams.get("game") ?? "",
    player: searchParams.get("player") ?? "",
    from: searchParams.get("from") ?? "",
    to: searchParams.get("to") ?? "",
  }
  const filtered = Object.values(filters).some(Boolean)

  /** Sets one filter in the address, or takes it out when the value is empty. */
  function setFilter(name: keyof typeof filters, value: string) {
    setSearchParams(
      (current) => {
        if (value) {
          current.set(name, value)
        } else {
          current.delete(name)
        }
        return current
      },
      // Changing a filter replaces the current step in the browser's history, so "back"
      // leaves the page instead of undoing the filters one by one.
      { replace: true },
    )
  }

  const games = api.useQuery("get", "/api/games")
  const players = api.useQuery("get", "/api/players")
  // "My plays" and "Player" together ask for the Plays that both took part in.
  const playerIds = [...new Set([filters.mine && account.playerId, filters.player])].filter(
    (id): id is string => Boolean(id),
  )
  // An infinite query fetches a list page by page and keeps the pages it has. The first page
  // is asked for with an empty "after"; each next one with the "next" of the page before it.
  // Changing a filter changes the query, so the list starts again from its first page.
  const history = api.useInfiniteQuery(
    "get",
    "/api/plays",
    {
      params: {
        query: {
          gameId: filters.game || undefined,
          playerId: playerIds.length > 0 ? playerIds : undefined,
          from: filters.from || undefined,
          to: filters.to || undefined,
        },
      },
    },
    {
      pageParamName: "after",
      initialPageParam: "",
      getNextPageParam: (lastPage) => lastPage.next,
    },
  )
  const plays = history.data?.pages.flatMap((page) => page.plays) ?? []

  return (
    <div className="space-y-6">
      <PageHeader title={t("plays.history.title")} description={t("plays.history.description")} />

      <fieldset className="space-y-3 rounded-lg border p-3">
        <legend className="sr-only">{t("plays.history.filters")}</legend>
        <CheckboxField
          label={t("plays.history.mine")}
          hint={t("plays.history.mineHint")}
          checked={filters.mine}
          onChange={(checked) => setFilter("mine", checked ? "1" : "")}
        />
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          <FilterField label={t("plays.history.game")}>
            {(id) => (
              <NativeSelect
                id={id}
                className="w-full"
                value={filters.game}
                onChange={(event) => setFilter("game", event.target.value)}
              >
                <NativeSelectOption value="">{t("plays.history.allGames")}</NativeSelectOption>
                {games.data?.map((game) => (
                  <NativeSelectOption key={game.id} value={game.id}>
                    {gameName(game)}
                  </NativeSelectOption>
                ))}
              </NativeSelect>
            )}
          </FilterField>
          <FilterField label={t("plays.history.player")}>
            {(id) => (
              <NativeSelect
                id={id}
                className="w-full"
                value={filters.player}
                onChange={(event) => setFilter("player", event.target.value)}
              >
                <NativeSelectOption value="">{t("plays.history.anyPlayer")}</NativeSelectOption>
                {players.data?.map((player) => (
                  <NativeSelectOption key={player.id} value={player.id}>
                    {player.name}
                  </NativeSelectOption>
                ))}
              </NativeSelect>
            )}
          </FilterField>
          <FilterField label={t("plays.history.from")}>
            {(id) => (
              <Input
                id={id}
                type="date"
                value={filters.from}
                onChange={(event) => setFilter("from", event.target.value)}
              />
            )}
          </FilterField>
          <FilterField label={t("plays.history.to")}>
            {(id) => (
              <Input
                id={id}
                type="date"
                value={filters.to}
                onChange={(event) => setFilter("to", event.target.value)}
              />
            )}
          </FilterField>
        </div>
        {filtered && (
          <Button
            variant="outline"
            size="sm"
            onClick={() => setSearchParams({}, { replace: true })}
          >
            {t("plays.history.clear")}
          </Button>
        )}
      </fieldset>

      <QueryState
        queries={[history]}
        loading={t("plays.history.loading")}
        failed={t("plays.history.loadFailed")}
      >
        {() =>
          plays.length === 0 ? (
            <p className="text-muted-foreground">
              {filtered ? t("plays.history.noMatch") : t("plays.history.empty")}
            </p>
          ) : (
            <div className="space-y-4">
              <ul className="divide-y rounded-lg border">
                {plays.map((play) => (
                  <li key={play.id}>
                    <PlaySummary play={play} />
                  </li>
                ))}
              </ul>
              {history.hasNextPage && (
                <Button
                  variant="outline"
                  disabled={history.isFetchingNextPage}
                  data-testid="show-more"
                  onClick={() => void history.fetchNextPage()}
                >
                  {t("plays.history.showMore")}
                </Button>
              )}
            </div>
          )
        }
      </QueryState>
    </div>
  )
}

/** A small label above a filter's control. `children` gets the id that ties the two together. */
function FilterField({ label, children }: { label: string; children: (id: string) => ReactNode }) {
  const id = useId()
  return (
    <Field>
      <FieldLabel htmlFor={id}>{label}</FieldLabel>
      {children(id)}
    </Field>
  )
}

/** One Play in the history: its Game and day, and how it ended. The whole of it is a link to the Play. */
function PlaySummary({ play }: { play: Play }) {
  const { t } = useTranslation()
  const gameName = useGameName()
  const texts = useGameModuleTexts()
  // How it was won, for a Game that can be won in more than one way. The Victory Type is one of
  // its module's, and so is its text.
  const victoryType =
    play.victoryType && play.game.moduleKey
      ? texts[`${play.game.moduleKey}.victoryType.${play.victoryType}`]
      : undefined
  // Best first: by Placing, or winners first when nobody was placed.
  const results = play.results.toSorted(
    (a, b) => (a.placing ?? (a.won ? 0 : 1)) - (b.placing ?? (b.won ? 0 : 1)),
  )

  return (
    <Link
      to={`/plays/${play.id}`}
      className="block space-y-1 px-3 py-2.5 hover:bg-muted/50"
      data-testid="play-summary"
    >
      <div className="flex items-baseline justify-between gap-3">
        <span>
          <span className="font-medium">{gameName(play.game)}</span>
          {victoryType && <span className="text-sm text-muted-foreground"> · {victoryType}</span>}
        </span>
        <span className="shrink-0 text-sm text-muted-foreground">{formatDay(play.playedOn)}</span>
      </div>
      {play.group ? (
        <p className="text-sm text-muted-foreground">
          <span className="font-medium text-foreground">
            {play.group.won ? t("plays.page.wonTogether") : t("plays.page.lostTogether")}
          </span>
          {play.group.total !== undefined && ` (${play.group.total})`}
          {": "}
          {results.map((result) => result.player.name).join(", ")}
        </p>
      ) : (
        <ul className="flex flex-wrap gap-x-4 gap-y-1 text-sm">
          {results.map((result) => (
            <li
              key={result.player.id}
              className={
                result.won
                  ? "flex items-center gap-1 font-medium"
                  : result.won === false
                    ? "flex items-center gap-1 text-muted-foreground"
                    : // A Solo Play for score only, which nobody wins or loses.
                      "flex items-center gap-1"
              }
            >
              {result.won && (
                <TrophyIcon role="img" aria-label={t("plays.page.winner")} className="size-3.5" />
              )}
              {result.player.name}
              {result.total !== undefined && <span>{result.total}</span>}
            </li>
          ))}
        </ul>
      )}
    </Link>
  )
}
