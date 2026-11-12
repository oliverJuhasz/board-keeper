import { useQueryClient } from "@tanstack/react-query"
import { useState } from "react"
import { useTranslation } from "react-i18next"
import { Link, useNavigate, useParams } from "react-router"
import { api, type Play, type Result } from "@/api/client"
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
import { useCan } from "@/features/session/useSession"
import { formatDateTime, formatDay } from "@/lib/dates"
import { playKey, playsKey } from "./playQueries"

/** One Play with its Results, at /plays/<id>. */
export function PlayPage() {
  const { t } = useTranslation()
  // The ":id" part of the address.
  const { id = "" } = useParams()
  const play = api.useQuery("get", "/api/plays/{id}", { params: { path: { id } } })

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
      queries={[play]}
      loading={t("plays.page.loading")}
      failed={t("plays.page.loadFailed")}
    >
      {() => play.data && <PlayDetails play={play.data} />}
    </QueryState>
  )
}

function PlayDetails({ play }: { play: Play }) {
  const { t } = useTranslation()
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
  // "Lowest score wins" is a Variant of the Generic Module, kept in the Play's Setup.
  const lowestScoreWins = play.setup.lowestScoreWins === "true"

  return (
    <div className="space-y-6">
      <PageHeader
        title={play.game.name}
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
        <Table aria-label={t("plays.page.results")}>
          <TableHeader>
            <TableRow>
              {points && <TableHead className="w-20">{t("plays.page.placing")}</TableHead>}
              <TableHead>{t("plays.page.player")}</TableHead>
              {points && <TableHead className="text-right">{t("plays.page.score")}</TableHead>}
            </TableRow>
          </TableHeader>
          <TableBody>
            {/* Best first: by Placing, or winners first when nobody was placed. Players who
                share a Placing stay in the order they were entered. */}
            {play.results
              .toSorted((a, b) => orderOf(a) - orderOf(b))
              .map((result) => (
                <TableRow key={result.player.id}>
                  {points && <TableCell>{result.placing}</TableCell>}
                  <TableCell className="font-medium">
                    <span className="flex items-center gap-2">
                      {result.player.name}
                      {result.won && <Badge variant="secondary">{t("plays.page.winner")}</Badge>}
                    </span>
                  </TableCell>
                  {points && <TableCell className="text-right">{result.total}</TableCell>}
                </TableRow>
              ))}
          </TableBody>
        </Table>
      )}

      {play.notes && (
        <section className="space-y-1">
          <h2 className="text-sm font-medium">{t("plays.page.notes")}</h2>
          {/* Keeps the line breaks the recorder typed. */}
          <p className="whitespace-pre-wrap text-muted-foreground">{play.notes}</p>
        </section>
      )}

      <p className="text-sm text-muted-foreground">
        {recordedAs({
          cooperative: play.cooperative,
          recordingMode: play.recordingMode,
          lowestScoreWins,
        })}
        {". "}
        {t("plays.page.recordedBy", {
          name: play.recordedBy.name,
          date: formatDateTime(play.recordedAt),
        })}
      </p>

      {deleting && (
        <ConfirmDialog
          title={t("plays.page.deleteDialog.title")}
          description={t("plays.page.deleteDialog.description", { game: play.game.name })}
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
