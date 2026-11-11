import { useQueryClient } from "@tanstack/react-query"
import { PlusIcon } from "lucide-react"
import { useState } from "react"
import { useTranslation } from "react-i18next"
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
import { useCan } from "@/features/session/useSession"
import { GameDialog } from "./GameDialog"

type OpenDialog = { kind: "add" } | { kind: "edit" | "remove"; game: Game } | undefined

/** The catalogue of Games: the list you pick from when recording a Play. */
export function GamesPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const can = useCan()
  const [dialog, setDialog] = useState<OpenDialog>()
  const games = api.useQuery("get", "/api/games")
  const close = () => setDialog(undefined)

  const remove = api.useMutation("delete", "/api/games/{id}", {
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["get", "/api/games"] })
      close()
    },
  })

  /** How a Game's Plays are recorded by default, in a few words. */
  function recordedAs(game: Game) {
    if (game.cooperative) {
      return game.recordingMode === "POINTS"
        ? t("games.recordedAs.cooperativeScore")
        : t("games.recordedAs.cooperativeWonLost")
    }
    if (game.recordingMode === "WINNER_ONLY") {
      return t("games.recordedAs.winnerOnly")
    }
    return game.lowestScoreWins
      ? t("games.recordedAs.pointsLowest")
      : t("games.recordedAs.pointsHighest")
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title={t("games.title")}
        description={t("games.description")}
        action={
          can("CREATE_GAMES") && (
            <Button onClick={() => setDialog({ kind: "add" })}>
              <PlusIcon />
              {t("games.add")}
            </Button>
          )
        }
      />

      <QueryState queries={[games]} loading={t("games.loading")} failed={t("games.loadFailed")}>
        {() =>
          games.data?.length === 0 ? (
            <p className="text-muted-foreground">{t("games.empty")}</p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t("games.name")}</TableHead>
                  <TableHead>{t("games.recorded")}</TableHead>
                  <TableHead />
                </TableRow>
              </TableHeader>
              <TableBody>
                {games.data?.map((game) => (
                  <TableRow key={game.id}>
                    <TableCell className="font-medium">{game.name}</TableCell>
                    <TableCell className="text-muted-foreground">{recordedAs(game)}</TableCell>
                    <TableCell className="text-right">
                      {(can("EDIT_GAMES") || can("REMOVE_GAMES")) && (
                        <ActionsMenu label={t("games.actions", { name: game.name })}>
                          {can("EDIT_GAMES") && (
                            <DropdownMenuItem onClick={() => setDialog({ kind: "edit", game })}>
                              {t("games.edit")}
                            </DropdownMenuItem>
                          )}
                          {can("REMOVE_GAMES") && (
                            <DropdownMenuItem
                              variant="destructive"
                              onClick={() => setDialog({ kind: "remove", game })}
                            >
                              {t("games.remove")}
                            </DropdownMenuItem>
                          )}
                        </ActionsMenu>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
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
