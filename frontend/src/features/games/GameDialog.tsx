import { zodResolver } from "@hookform/resolvers/zod"
import { useQueryClient } from "@tanstack/react-query"
import { useState } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { z } from "zod"
import { api, type Game } from "@/api/client"
import { FormProblem } from "@/components/FormProblem"
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
import { asksWhoWins, type Recording } from "./recordedAs"
import { RecordingChoices } from "./RecordingChoices"

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
  const [recording, setRecording] = useState<Recording>({
    cooperative: game?.cooperative ?? false,
    recordingMode: game?.recordingMode ?? "POINTS",
    lowestScoreWins: game?.lowestScoreWins ?? false,
  })

  const onSuccess = () => {
    void queryClient.invalidateQueries({ queryKey: ["get", "/api/games"] })
    onClose()
  }
  const create = api.useMutation("post", "/api/games", { onSuccess })
  const update = api.useMutation("put", "/api/games/{id}", { onSuccess })

  const submit = form.handleSubmit(({ name }) => {
    const body = {
      name,
      ...recording,
      // A choice made earlier, and hidden since, doesn't count.
      lowestScoreWins: asksWhoWins(recording) && recording.lowestScoreWins,
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
            <RecordingChoices value={recording} onChange={setRecording} />
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
