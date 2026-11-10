import { useQueryClient } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { api, type Player } from "@/api/client"
import { FormProblem } from "@/components/FormProblem"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"

/** Asks before removing a Player, because it can't be undone. */
export function RemovePlayerDialog({ player, onClose }: { player: Player; onClose: () => void }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const remove = api.useMutation("delete", "/api/players/{id}", {
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["get", "/api/players"] })
      void queryClient.invalidateQueries({ queryKey: ["get", "/api/invites"] })
      onClose()
    },
  })

  return (
    <AlertDialog open onOpenChange={(open) => !open && onClose()}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>
            {t("players.removeDialog.title", { name: player.name })}
          </AlertDialogTitle>
          <AlertDialogDescription>{t("players.removeDialog.description")}</AlertDialogDescription>
        </AlertDialogHeader>
        <FormProblem error={remove.error} />
        <AlertDialogFooter>
          <AlertDialogCancel>{t("common.cancel")}</AlertDialogCancel>
          <AlertDialogAction
            variant="destructive"
            disabled={remove.isPending}
            onClick={() => remove.mutate({ params: { path: { id: player.id } } })}
          >
            {t("players.removeDialog.confirm")}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
