import { zodResolver } from "@hookform/resolvers/zod"
import { useQueryClient } from "@tanstack/react-query"
import type { ComponentProps } from "react"
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

/**
 * The dialog for a Player's name: adds a Player, or renames the one it is given.
 *
 * @param onAdded called with the new Player once it has been added, before the dialog closes
 * @param finalFocus where focus goes once the dialog has closed, such as the button that opened
 *   it. "Dialogs" in docs/development.md says when a dialog needs this.
 */
export function PlayerNameDialog({
  player,
  onAdded,
  onClose,
  finalFocus,
}: {
  player?: Player
  onAdded?: (player: Player) => void
  onClose: () => void
  finalFocus?: ComponentProps<typeof DialogContent>["finalFocus"]
}) {
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
  const create = api.useMutation("post", "/api/players", {
    onSuccess: (added) => {
      onAdded?.(added)
      onSuccess()
    },
  })
  const rename = api.useMutation("put", "/api/players/{id}", { onSuccess })

  const submit = form.handleSubmit((values) =>
    player
      ? rename.mutate({ params: { path: { id: player.id } }, body: values })
      : create.mutate({ body: values }),
  )

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent finalFocus={finalFocus} data-testid="player-name-dialog">
        {/* The dialog can be opened from inside another form. React passes events up through
            the components, not the page, so without stopPropagation submitting this form would
            submit that one too. */}
        <form
          onSubmit={(event) => {
            event.stopPropagation()
            void submit(event)
          }}
          noValidate
        >
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
              data-testid="player-name"
              {...form.register("name")}
            />
            <DialogFooter>
              <DialogClose render={<Button type="button" variant="outline" />}>
                {t("common.cancel")}
              </DialogClose>
              <Button
                type="submit"
                disabled={create.isPending || rename.isPending}
                data-testid="save-player"
              >
                {player ? t("players.renameDialog.submit") : t("players.addDialog.submit")}
              </Button>
            </DialogFooter>
          </FieldGroup>
        </form>
      </DialogContent>
    </Dialog>
  )
}
