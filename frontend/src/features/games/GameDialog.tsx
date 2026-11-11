import { zodResolver } from "@hookform/resolvers/zod"
import { useQueryClient } from "@tanstack/react-query"
import { useState } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { z } from "zod"
import { api, type Game } from "@/api/client"
import { FormProblem } from "@/components/FormProblem"
import { RadioField } from "@/components/RadioField"
import { TextField } from "@/components/TextField"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { FieldGroup } from "@/components/ui/field"
import { gameName } from "@/features/session/accountRules"

const gameForm = z.object({ name: gameName })

/**
 * The dialog for a Game: adds one to the catalogue, or changes the one it is given. Besides the
 * name, it asks how the Game's Plays are recorded by default.
 */
export function GameDialog({ game, onClose }: { game?: Game; onClose: () => void }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const form = useForm({
    resolver: zodResolver(gameForm),
    defaultValues: { name: game?.name ?? "" },
  })
  const [kind, setKind] = useState<"competitive" | "cooperative">(
    game?.cooperative ? "cooperative" : "competitive",
  )
  const [recordingMode, setRecordingMode] = useState(game?.recordingMode ?? "POINTS")
  const [winner, setWinner] = useState<"highest" | "lowest">(
    game?.lowestScoreWins ? "lowest" : "highest",
  )

  const onSuccess = () => {
    void queryClient.invalidateQueries({ queryKey: ["get", "/api/games"] })
    onClose()
  }
  const create = api.useMutation("post", "/api/games", { onSuccess })
  const update = api.useMutation("put", "/api/games/{id}", { onSuccess })

  const cooperative = kind === "cooperative"
  // "Who wins" only makes sense when players compete on points.
  const asksWhoWins = !cooperative && recordingMode === "POINTS"

  const submit = form.handleSubmit(({ name }) => {
    const body = {
      name,
      cooperative,
      recordingMode,
      lowestScoreWins: asksWhoWins && winner === "lowest",
    }
    if (game) {
      update.mutate({ params: { path: { id: game.id } }, body })
    } else {
      create.mutate({ body })
    }
  })

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        <form onSubmit={submit} noValidate>
          <FieldGroup>
            <DialogHeader>
              <DialogTitle>
                {game
                  ? t("games.dialog.editTitle", { name: game.name })
                  : t("games.dialog.addTitle")}
              </DialogTitle>
              <DialogDescription>
                {game ? t("games.dialog.defaultsHint") : t("games.dialog.addDescription")}
              </DialogDescription>
            </DialogHeader>
            <FormProblem error={create.error ?? update.error} />
            <TextField
              label={t("games.name")}
              autoComplete="off"
              autoFocus
              error={form.formState.errors.name}
              {...form.register("name")}
            />
            <RadioField
              legend={t("games.dialog.kind")}
              value={kind}
              onChange={setKind}
              options={[
                {
                  value: "competitive",
                  label: t("games.dialog.competitive"),
                  hint: t("games.dialog.competitiveHint"),
                },
                {
                  value: "cooperative",
                  label: t("games.dialog.cooperative"),
                  hint: t("games.dialog.cooperativeHint"),
                },
              ]}
            />
            <RadioField
              legend={t("games.dialog.recording")}
              value={recordingMode}
              onChange={setRecordingMode}
              options={[
                {
                  value: "POINTS",
                  label: t("games.dialog.points"),
                  hint: t("games.dialog.pointsHint"),
                },
                {
                  value: "WINNER_ONLY",
                  label: cooperative
                    ? t("games.dialog.wonOrLostOnly")
                    : t("games.dialog.winnerOnly"),
                  hint: t("games.dialog.outcomeHint"),
                },
              ]}
            />
            {asksWhoWins && (
              <RadioField
                legend={t("games.dialog.winner")}
                value={winner}
                onChange={setWinner}
                options={[
                  { value: "highest", label: t("games.dialog.highest") },
                  { value: "lowest", label: t("games.dialog.lowest") },
                ]}
              />
            )}
            <DialogFooter>
              <DialogClose render={<Button type="button" variant="outline" />}>
                {t("common.cancel")}
              </DialogClose>
              <Button type="submit" disabled={create.isPending || update.isPending}>
                {game ? t("games.dialog.submitEdit") : t("games.dialog.submitAdd")}
              </Button>
            </DialogFooter>
          </FieldGroup>
        </form>
      </DialogContent>
    </Dialog>
  )
}
