import { useQueryClient } from "@tanstack/react-query"
import { useId, useState } from "react"
import { useTranslation } from "react-i18next"
import { api, type Player } from "@/api/client"
import { LinkDialog } from "@/components/LinkDialog"
import { Field, FieldLabel } from "@/components/ui/field"
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select"
import { roleName } from "@/features/roles/roleName"
import { useCan } from "@/features/session/useSession"
import { formatDate } from "@/lib/dates"

/** Invites a Player to register an Account, and shows the link to send them. */
export function InviteDialog({ player, onClose }: { player: Player; onClose: () => void }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const can = useCan()
  const roleFieldId = useId()

  // Giving a Role other than Member needs the Permission to manage Accounts.
  const mayChooseRole = can("MANAGE_ACCOUNTS")
  const roles = api.useQuery("get", "/api/roles", {}, { enabled: mayChooseRole })
  const memberRoleId = roles.data?.find((role) => role.builtIn === "MEMBER")?.id
  const [chosenRoleId, setChosenRoleId] = useState<string>()

  const invite = api.useMutation("post", "/api/invites", {
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["get", "/api/invites"] }),
  })
  const name = player.name

  return (
    <LinkDialog
      ask={{
        title: t("players.inviteDialog.title", { name }),
        description: t("players.inviteDialog.description", { name }),
        submit: t("players.inviteDialog.submit"),
      }}
      onCreate={() => invite.mutate({ body: { playerId: player.id, roleId: chosenRoleId } })}
      creating={invite.isPending}
      error={invite.error}
      ready={
        invite.data && {
          title: t("players.inviteDialog.readyTitle", { name }),
          description: t("players.inviteDialog.readyDescription", {
            name,
            date: formatDate(invite.data.invite.expiresAt),
          }),
          label: t("players.inviteDialog.link"),
          link: `${window.location.origin}/register/${invite.data.token}`,
        }
      }
      onClose={onClose}
    >
      {mayChooseRole && roles.data && (
        <Field>
          <FieldLabel htmlFor={roleFieldId}>{t("players.inviteDialog.role")}</FieldLabel>
          <NativeSelect
            id={roleFieldId}
            className="w-full"
            value={chosenRoleId ?? memberRoleId}
            onChange={(event) => setChosenRoleId(event.target.value)}
          >
            {roles.data.map((role) => (
              <NativeSelectOption key={role.id} value={role.id}>
                {roleName(t, role)}
              </NativeSelectOption>
            ))}
          </NativeSelect>
        </Field>
      )}
    </LinkDialog>
  )
}
