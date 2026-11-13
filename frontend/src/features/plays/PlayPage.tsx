import { useQueryClient } from "@tanstack/react-query"
import { useState } from "react"
import { useTranslation } from "react-i18next"
import { Link, useNavigate, useParams } from "react-router"
import { api, type GameModule, type Play, type Result } from "@/api/client"
import { problemCode } from "@/api/problems"
import { ActionsMenu } from "@/components/ActionsMenu"
import { ConfirmDialog } from "@/components/ConfirmDialog"
import { LinkButton } from "@/components/LinkButton"
import { PageHeader } from "@/components/PageHeader"
import { PageMessage } from "@/components/PageMessage"
import { QueryState } from "@/components/QueryState"
import { Badge } from "@/components/ui/badge"
import { DropdownMenuItem } from "@/components/ui/dropdown-menu"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { useRecordedAs } from "@/features/games/recordedAs"
import { GENERIC, moduleWithId, useGameModules, useGameName } from "@/features/games/useGameModules"
import { useGameModuleTexts, useGameModuleTextsQuery } from "@/features/games/useGameModuleTexts"
import { useCan } from "@/features/session/useSession"
import { formatDateTime, formatDay } from "@/lib/dates"
import { playKey, playsKey } from "./playQueries"

/** One Play with its Results, at /plays/<id>. */
export function PlayPage() {
  const { t } = useTranslation()
  // The ":id" part of the address.
  const { id = "" } = useParams()
  const play = api.useQuery("get", "/api/plays/{id}", { params: { path: { id } } })
  const modules = useGameModules()
  const texts = useGameModuleTextsQuery()

  if (problemCode(play.error) === "plays.notFound") {
    return (
      <PageMessage title={t("problems.plays.notFound")}>
        <Link to="/plays" className="text-foreground underline underline-offset-4">
          {t("plays.page.backToPlays")}
        </Link>
      </PageMessage>
    )
  }
  return (
    <QueryState
      queries={[play, modules, texts]}
      loading={t("plays.page.loading")}
      failed={t("plays.page.loadFailed")}
    >
      {() =>
        play.data && (
          <PlayDetails
            play={play.data}
            module={moduleWithId(modules.data, play.data.gameModuleId)}
          />
        )
      }
    </QueryState>
  )
}

/**
 * @param module the Game Module that scored the Play, for the names of its Score Categories
 *     and Variants. Undefined when it is not part of this build: the totals are shown anyway.
 */
function PlayDetails({ play, module }: { play: Play; module?: GameModule }) {
  const { t } = useTranslation()
  const gameName = useGameName()
  const texts = useGameModuleTexts()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const can = useCan()
  const recordedAs = useRecordedAs()
  const [deleting, setDeleting] = useState(false)
  const points = play.recordingMode === "POINTS"

  const remove = api.useMutation("delete", "/api/plays/{id}", {
    onSuccess: () => {
      // Throw away what was fetched about the Play and the history, so neither is shown again.
      void navigate("/plays")
      queryClient.removeQueries({ queryKey: playKey(play.id) })
      queryClient.removeQueries({ queryKey: playsKey })
    },
  })
  const moduleText = (name: string) => (module ? texts[`${module.key}.${name}`] : undefined)
  // The rows of the pad that were filled in. With one row, the total says it all.
  const filledIn = (module?.scorePad.categories ?? []).filter((category) =>
    play.results.some((result) => result.scores[category.key] !== undefined),
  )
  const categories = filledIn.length > 1 ? filledIn : []
  // Best first: by Placing, or winners first when nobody was placed. Players who share a
  // Placing stay in the order they were entered.
  const ranked = play.results.toSorted((a, b) => orderOf(a) - orderOf(b))

  // How the Play was recorded, in a few words. For a Game without a module of its own those
  // are its recording choices, where "lowest score wins" is a Variant kept in the Setup. For
  // any other Game they are the Variants that were chosen.
  // A Play of one that isn't cooperative is a Solo Play: it has no Placing, and is won or lost
  // against an opponent, or played for a score only.
  const solo = !play.cooperative && play.results.length === 1
  const variantsChosen = (module?.scorePad.variants ?? []).flatMap((variant) => {
    if (variant.soloOnly && !solo) {
      return []
    }
    const value = play.setup[variant.key] ?? variant.defaultValue
    if (variant.kind === "ON_OFF") {
      return value === "true" ? [moduleText(`variant.${variant.key}`)] : []
    }
    return [
      `${moduleText(`variant.${variant.key}`)}: ${moduleText(`variant.${variant.key}.${value}`)}`,
    ]
  })
  const howRecorded =
    module?.key === GENERIC
      ? recordedAs({
          cooperative: play.cooperative,
          recordingMode: play.recordingMode,
          lowestScoreWins: play.setup.lowestScoreWins === "true",
        })
      : [
          points
            ? t("recording.points")
            : solo
              ? t("recording.wonOrLostOnly")
              : t("recording.winnerOnly"),
          ...variantsChosen,
        ].join(", ")

  return (
    <div className="space-y-6">
      <PageHeader
        title={
          // The Game's name leads to its statistics. The faint underline shows that it is a
          // link, also on a phone, where nothing changes under a finger before the tap.
          <Link
            to={`/games/${play.game.id}`}
            className="underline decoration-muted-foreground/40 underline-offset-4 hover:decoration-foreground"
          >
            {gameName(play.game)}
          </Link>
        }
        description={[formatDay(play.playedOn), play.location].filter(Boolean).join(" · ")}
        action={
          <div className="flex items-center gap-1">
            {can("CREATE_PLAYS") && (
              <LinkButton to={`/plays/new?game=${play.game.id}`}>
                {t("plays.page.recordAnother")}
              </LinkButton>
            )}
            {/* The server says whether this Account may change this particular Play: that
                depends on who recorded it and who played in it, not on a Permission alone. */}
            {play.canChange && (
              <ActionsMenu label={t("plays.page.actions")}>
                <DropdownMenuItem render={<Link to={`/plays/${play.id}/edit`} />}>
                  {t("plays.page.edit")}
                </DropdownMenuItem>
                <DropdownMenuItem variant="destructive" onClick={() => setDeleting(true)}>
                  {t("plays.page.delete")}
                </DropdownMenuItem>
              </ActionsMenu>
            )}
          </div>
        }
      />

      {play.group ? (
        <section className="space-y-3">
          <div className="flex items-baseline gap-3">
            <p className="font-heading text-lg font-semibold">
              {play.group.won ? t("plays.page.wonTogether") : t("plays.page.lostTogether")}
            </p>
            {play.group.total !== undefined && (
              <p className="text-muted-foreground">
                {t("plays.page.groupScore", { score: play.group.total })}
              </p>
            )}
          </div>
          <div className="space-y-1">
            <h2 className="text-sm font-medium">{t("plays.page.players")}</h2>
            <ul className="text-muted-foreground">
              {play.results.map((result) => (
                <li key={result.player.id}>{result.player.name}</li>
              ))}
            </ul>
          </div>
        </section>
      ) : (
        <>
          <Table aria-label={t("plays.page.results")}>
            <TableHeader>
              <TableRow>
                {points && !solo && (
                  <TableHead className="w-20">{t("plays.page.placing")}</TableHead>
                )}
                <TableHead>{t("plays.page.player")}</TableHead>
                {points && (
                  <TableHead className="text-right">
                    {categories.length > 0 ? t("plays.page.total") : t("plays.page.score")}
                  </TableHead>
                )}
              </TableRow>
            </TableHeader>
            <TableBody>
              {ranked.map((result) => (
                <TableRow key={result.player.id}>
                  {points && !solo && <TableCell>{result.placing}</TableCell>}
                  <TableCell className="font-medium">
                    <span className="flex items-center gap-2">
                      {result.player.name}
                      {/* A Solo Play against an opponent is won or lost; one for score only
                          is neither, and has no badge. */}
                      {solo
                        ? result.won !== undefined && (
                            <Badge variant={result.won ? "secondary" : "outline"}>
                              {result.won ? t("plays.page.won") : t("plays.page.lost")}
                            </Badge>
                          )
                        : result.won && <Badge variant="secondary">{t("plays.page.winner")}</Badge>}
                    </span>
                  </TableCell>
                  {points && <TableCell className="text-right">{result.total}</TableCell>}
                </TableRow>
              ))}
            </TableBody>
          </Table>

          {/* The score pad as it was filled in, laid out like the paper one and the form: a row
              for each Score Category and a column for each Player. A Game can have many rows,
              and they fit this way round, where a column for each would not. */}
          {categories.length > 0 && (
            <section className="space-y-2">
              <h2 className="text-sm font-medium">{t("plays.page.pad")}</h2>
              <Table aria-label={t("plays.page.pad")}>
                <TableHeader>
                  <TableRow>
                    <TableHead />
                    {ranked.map((result) => (
                      <TableHead
                        key={result.player.id}
                        className="max-w-16 truncate text-right sm:max-w-28"
                      >
                        {result.player.name}
                      </TableHead>
                    ))}
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {categories.map((category) => (
                    <TableRow key={category.key}>
                      {/* A long name, such as "Nature's Spirit card", may wrap on a phone. */}
                      <TableHead
                        scope="row"
                        className={
                          category.countsTowardTotal
                            ? "font-normal whitespace-normal"
                            : "font-normal whitespace-normal text-muted-foreground italic"
                        }
                      >
                        {moduleText(`category.${category.key}`)}
                      </TableHead>
                      {ranked.map((result) => (
                        <TableCell
                          key={result.player.id}
                          className="text-right text-muted-foreground tabular-nums"
                        >
                          {result.scores[category.key]}
                        </TableCell>
                      ))}
                    </TableRow>
                  ))}
                  <TableRow className="font-medium">
                    <TableHead scope="row">{t("plays.page.total")}</TableHead>
                    {ranked.map((result) => (
                      <TableCell key={result.player.id} className="text-right tabular-nums">
                        {result.total}
                      </TableCell>
                    ))}
                  </TableRow>
                </TableBody>
              </Table>
            </section>
          )}
        </>
      )}

      {play.notes && (
        <section className="space-y-1">
          <h2 className="text-sm font-medium">{t("plays.page.notes")}</h2>
          {/* Keeps the line breaks the recorder typed. */}
          <p className="whitespace-pre-wrap text-muted-foreground">{play.notes}</p>
        </section>
      )}

      <p className="text-sm text-muted-foreground">
        {howRecorded}
        {". "}
        {t("plays.page.recordedBy", {
          name: play.recordedBy.name,
          date: formatDateTime(play.recordedAt),
        })}
      </p>

      {deleting && (
        <ConfirmDialog
          title={t("plays.page.deleteDialog.title")}
          description={t("plays.page.deleteDialog.description", { game: gameName(play.game) })}
          confirm={t("plays.page.deleteDialog.confirm")}
          onConfirm={() => remove.mutate({ params: { path: { id: play.id } } })}
          pending={remove.isPending}
          error={remove.error}
          onClose={() => setDeleting(false)}
        />
      )}
    </div>
  )
}

/** Where a Result comes in the list: its Placing, or failing that, winners before the others. */
function orderOf(result: Result) {
  return result.placing ?? (result.won ? 0 : 1)
}
