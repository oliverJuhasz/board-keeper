import { zodResolver } from "@hookform/resolvers/zod"
import { useQueryClient } from "@tanstack/react-query"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { z } from "zod"
import { api, type Player } from "@/api/client"
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
import { playerName } from "@/features/session/accountRules"

const nameForm = z.object({ name: playerName })

/** The dialog for a Player's name: adds a Player, or renames the one it is given. */
export function PlayerNameDialog({ player, onClose }: { player?: Player; onClose: () => void }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const form = useForm({
    resolver: zodResolver(nameForm),
    defaultValues: { name: player?.name ?? "" },
  })

  // After a change, mark the list of Players as out of date, so it is loaded again.
  const onSuccess = () => {
    void queryClient.invalidateQueries({ queryKey: ["get", "/api/players"] })
    onClose()
  }
  const create = api.useMutation("post", "/api/players", { onSuccess })
  const rename = api.useMutation("put", "/api/players/{id}", { onSuccess })

  const submit = form.handleSubmit((values) =>
    player
      ? rename.mutate({ params: { path: { id: player.id } }, body: values })
      : create.mutate({ body: values }),
  )

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        <form onSubmit={submit} noValidate>
          <FieldGroup>
            <DialogHeader>
              <DialogTitle>
                {player
                  ? t("players.renameDialog.title", { name: player.name })
                  : t("players.addDialog.title")}
              </DialogTitle>
              {!player && (
                <DialogDescription>{t("players.addDialog.description")}</DialogDescription>
              )}
            </DialogHeader>
            <FormProblem error={create.error ?? rename.error} />
            <TextField
              label={t("players.name")}
              autoComplete="off"
              autoFocus
              error={form.formState.errors.name}
              {...form.register("name")}
            />
            <DialogFooter>
              <DialogClose render={<Button type="button" variant="outline" />}>
                {t("common.cancel")}
              </DialogClose>
              <Button type="submit" disabled={create.isPending || rename.isPending}>
                {player ? t("players.renameDialog.submit") : t("players.addDialog.submit")}
              </Button>
            </DialogFooter>
          </FieldGroup>
        </form>
      </DialogContent>
    </Dialog>
  )
}
