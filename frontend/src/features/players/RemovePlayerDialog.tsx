import { useQueryClient } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { api, type Player } from "@/api/client"
import { ConfirmDialog } from "@/components/ConfirmDialog"

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
    <ConfirmDialog
      title={t("players.removeDialog.title", { name: player.name })}
      description={t("players.removeDialog.description")}
      confirm={t("players.removeDialog.confirm")}
      onConfirm={() => remove.mutate({ params: { path: { id: player.id } } })}
      pending={remove.isPending}
      error={remove.error}
      onClose={onClose}
    />
  )
}
