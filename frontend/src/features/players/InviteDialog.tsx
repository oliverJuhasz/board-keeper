import { useQueryClient } from "@tanstack/react-query"
import { CheckIcon, CopyIcon } from "lucide-react"
import { useId, useState } from "react"
import { useTranslation } from "react-i18next"
import { api, type Player } from "@/api/client"
import { FormProblem } from "@/components/FormProblem"
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
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select"
import { roleName } from "@/features/roles/roleName"
import { useCan } from "@/features/session/useSession"
import { formatDate } from "@/lib/dates"

/**
 * Invites a Player to register an Account. First it asks, then it shows the link, which the
 * person who created it has to send on themselves: Board Keeper sends no email.
 */
export function InviteDialog({ player, onClose }: { player: Player; onClose: () => void }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const can = useCan()
  const roleFieldId = useId()
  const linkFieldId = useId()
  const [copied, setCopied] = useState(false)

  // Giving a Role other than Member needs the Permission to manage Accounts.
  const mayChooseRole = can("MANAGE_ACCOUNTS")
  const roles = api.useQuery("get", "/api/roles", {}, { enabled: mayChooseRole })
  const memberRoleId = roles.data?.find((role) => role.builtIn === "MEMBER")?.id
  const [chosenRoleId, setChosenRoleId] = useState<string>()

  const invite = api.useMutation("post", "/api/invites", {
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["get", "/api/invites"] }),
  })
  const created = invite.data
  const link = created && `${window.location.origin}/register/${created.token}`

  async function copy() {
    if (link) {
      await navigator.clipboard.writeText(link)
      setCopied(true)
    }
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        {created && link ? (
          <FieldGroup>
            <DialogHeader>
              <DialogTitle>
                {t("players.inviteDialog.readyTitle", { name: player.name })}
              </DialogTitle>
              <DialogDescription>
                {t("players.inviteDialog.readyDescription", {
                  name: player.name,
                  date: formatDate(created.invite.expiresAt),
                })}
              </DialogDescription>
            </DialogHeader>
            <Field>
              <FieldLabel htmlFor={linkFieldId}>{t("players.inviteDialog.link")}</FieldLabel>
              <Input
                id={linkFieldId}
                readOnly
                value={link}
                onFocus={(event) => event.target.select()}
              />
            </Field>
            <DialogFooter>
              <Button variant="outline" onClick={() => void copy()}>
                {copied ? <CheckIcon /> : <CopyIcon />}
                {copied ? t("players.inviteDialog.copied") : t("players.inviteDialog.copy")}
              </Button>
              <DialogClose render={<Button />}>{t("players.inviteDialog.done")}</DialogClose>
            </DialogFooter>
          </FieldGroup>
        ) : (
          <FieldGroup>
            <DialogHeader>
              <DialogTitle>{t("players.inviteDialog.title", { name: player.name })}</DialogTitle>
              <DialogDescription>
                {t("players.inviteDialog.description", { name: player.name })}
              </DialogDescription>
            </DialogHeader>
            <FormProblem error={invite.error} />
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
            <DialogFooter>
              <DialogClose render={<Button variant="outline" />}>{t("common.cancel")}</DialogClose>
              <Button
                disabled={invite.isPending}
                onClick={() =>
                  invite.mutate({ body: { playerId: player.id, roleId: chosenRoleId } })
                }
              >
                {t("players.inviteDialog.submit")}
              </Button>
            </DialogFooter>
          </FieldGroup>
        )}
      </DialogContent>
    </Dialog>
  )
}
