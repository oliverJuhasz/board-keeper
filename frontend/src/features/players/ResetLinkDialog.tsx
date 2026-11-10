import { useTranslation } from "react-i18next"
import { api, type Player } from "@/api/client"
import { LinkDialog } from "@/components/LinkDialog"
import { formatDateTime } from "@/lib/dates"

/** Creates a Reset Link for the Account of a Player who forgot their password. */
export function ResetLinkDialog({ player, onClose }: { player: Player; onClose: () => void }) {
  const { t } = useTranslation()
  const create = api.useMutation("post", "/api/reset-links")
  const name = player.name

  return (
    <LinkDialog
      ask={{
        title: t("players.resetDialog.title", { name }),
        description: t("players.resetDialog.description", { name }),
        submit: t("players.resetDialog.submit"),
      }}
      onCreate={() => create.mutate({ body: { playerId: player.id } })}
      creating={create.isPending}
      error={create.error}
      ready={
        create.data && {
          title: t("players.resetDialog.readyTitle", { name }),
          description: t("players.resetDialog.readyDescription", {
            name,
            date: formatDateTime(create.data.expiresAt),
          }),
          label: t("players.resetDialog.link"),
          link: `${window.location.origin}/reset-password/${create.data.token}`,
        }
      }
      onClose={onClose}
    />
  )
}
