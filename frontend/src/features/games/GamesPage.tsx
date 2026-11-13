import { useQueryClient } from "@tanstack/react-query"
import { ChevronRightIcon, PlusIcon } from "lucide-react"
import { useState } from "react"
import { useTranslation } from "react-i18next"
import { Link } from "react-router"
import { api, type Game } from "@/api/client"
import { ActionsMenu } from "@/components/ActionsMenu"
import { ConfirmDialog } from "@/components/ConfirmDialog"
import { PageHeader } from "@/components/PageHeader"
import { QueryState } from "@/components/QueryState"
import { Button } from "@/components/ui/button"
import { DropdownMenuItem } from "@/components/ui/dropdown-menu"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { PlayAgain } from "@/features/plays/PlayAgain"
import { useCan } from "@/features/session/useSession"
import { formatRecentDay } from "@/lib/dates"
import { GameDialog } from "./GameDialog"
import { useRecordedAs } from "./recordedAs"
import { useGameName } from "./useGameModules"

type OpenDialog = { kind: "add" } | { kind: "edit" | "remove"; game: Game } | undefined

/**
 * The start page: where a Play is recorded from, in as few taps as possible. Recent Plays are
 * offered to play again with the same Players. Below them come the Games, the most recently
 * played first, and a tap on a Game opens the form for a Play of it.
 *
 * It is also the catalogue of Games, where they are added, edited and removed.
 */
export function GamesPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const can = useCan()
  const recordedAs = useRecordedAs()
  const gameName = useGameName()
  const [dialog, setDialog] = useState<OpenDialog>()
  const games = api.useQuery("get", "/api/games")
  const lastPlayed = api.useQuery("get", "/api/plays/last-played")
  const close = () => setDialog(undefined)
  const records = can("CREATE_PLAYS")

  /** The day a Game was last played, or undefined if it never was. */
  const lastPlayedOn = (game: Game) =>
    lastPlayed.data?.find((entry) => entry.gameId === game.id)?.playedOn
  // The most recently played first. Games never played follow, in the catalogue's order, by
  // name. toSorted() keeps the order of Games it calls equal.
  const recentFirst = (games.data ?? []).toSorted((a, b) =>
    (lastPlayedOn(b) ?? "").localeCompare(lastPlayedOn(a) ?? ""),
  )

  const remove = api.useMutation("delete", "/api/games/{id}", {
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["get", "/api/games"] })
      close()
    },
  })

  return (
    <div className="space-y-6">
      <PageHeader
        title={records ? t("games.startTitle") : t("games.title")}
        description={records ? t("games.startDescription") : t("games.description")}
        action={
          can("CREATE_GAMES") && (
            <Button onClick={() => setDialog({ kind: "add" })}>
              <PlusIcon />
              {t("games.add")}
            </Button>
          )
        }
      />

      <QueryState
        queries={[games, lastPlayed]}
        loading={t("games.loading")}
        failed={t("games.loadFailed")}
      >
        {() =>
          games.data?.length === 0 ? (
            <p className="text-muted-foreground">{t("games.empty")}</p>
          ) : (
            <div className="space-y-6">
              {records && <PlayAgain games={games.data ?? []} />}
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t("games.name")}</TableHead>
                    {/* Left out on a phone, where the row needs its width for the buttons. */}
                    <TableHead className="hidden sm:table-cell">{t("games.recorded")}</TableHead>
                    <TableHead />
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {recentFirst.map((game) => (
                    // "relative" lets the Game's link stretch over the whole row, so a tap
                    // anywhere on it opens the form. The menu is lifted above the link.
                    <TableRow key={game.id} className="relative">
                      <TableCell>
                        {records ? (
                          <Link
                            to={`/plays/new?game=${game.id}`}
                            aria-label={t("plays.record.actionFor", { name: gameName(game) })}
                            className="font-medium outline-none after:absolute after:inset-0 after:rounded-md focus-visible:after:ring-2 focus-visible:after:ring-ring"
                          >
                            {gameName(game)}
                          </Link>
                        ) : (
                          <span className="font-medium">{gameName(game)}</span>
                        )}
                        <span className="block text-sm text-muted-foreground">
                          {lastPlayedOn(game)
                            ? t("games.lastPlayed", {
                                when: formatRecentDay(lastPlayedOn(game) ?? ""),
                              })
                            : t("games.neverPlayed")}
                        </span>
                      </TableCell>
                      <TableCell className="hidden text-muted-foreground sm:table-cell">
                        {/* A Game with a module of its own has no recording choices to show. */}
                        {game.hasOwnModule ? t("games.ownScorePad") : recordedAs(game)}
                      </TableCell>
                      <TableCell>
                        <div className="relative z-10 flex items-center justify-end gap-1">
                          <ActionsMenu label={t("games.actions", { name: gameName(game) })}>
                            <DropdownMenuItem render={<Link to={`/games/${game.id}`} />}>
                              {t("games.statistics")}
                            </DropdownMenuItem>
                            {/* A Game with a module of its own is named by the module and stays
                                while the module does: there is nothing to edit or remove. */}
                            {!game.hasOwnModule && can("EDIT_GAMES") && (
                              <DropdownMenuItem onClick={() => setDialog({ kind: "edit", game })}>
                                {t("games.edit")}
                              </DropdownMenuItem>
                            )}
                            {!game.hasOwnModule && can("REMOVE_GAMES") && (
                              <DropdownMenuItem
                                variant="destructive"
                                onClick={() => setDialog({ kind: "remove", game })}
                              >
                                {t("games.remove")}
                              </DropdownMenuItem>
                            )}
                          </ActionsMenu>
                          {records && (
                            <ChevronRightIcon
                              aria-hidden
                              className="size-4 text-muted-foreground"
                            />
                          )}
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )
        }
      </QueryState>

      {dialog?.kind === "add" && <GameDialog onClose={close} />}
      {dialog?.kind === "edit" && <GameDialog game={dialog.game} onClose={close} />}
      {dialog?.kind === "remove" && (
        <ConfirmDialog
          title={t("games.removeDialog.title", { name: dialog.game.name })}
          description={t("games.removeDialog.description")}
          confirm={t("games.removeDialog.confirm")}
          onConfirm={() => remove.mutate({ params: { path: { id: dialog.game.id } } })}
          pending={remove.isPending}
          error={remove.error}
          onClose={close}
        />
      )}
    </div>
  )
}
