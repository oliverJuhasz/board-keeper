import { useQueryClient } from "@tanstack/react-query"
import { useState } from "react"
import { useTranslation } from "react-i18next"
import { api, type Account, type Role } from "@/api/client"
import { CheckboxField } from "@/components/CheckboxField"
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
import { FieldGroup } from "@/components/ui/field"
import { roleName } from "@/features/roles/roleName"
import { useCan } from "@/features/session/useSession"

/** Chooses which Roles an Account holds. */
export function AccountRolesDialog({
  account,
  roles,
  onClose,
}: {
  account: Account
  roles: Role[]
  onClose: () => void
}) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const can = useCan()
  const [roleIds, setRoleIds] = useState(account.roleIds)

  const save = api.useMutation("put", "/api/accounts/{id}/roles", {
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["get", "/api/accounts"] })
      onClose()
    },
  })

  function toggle(roleId: string, checked: boolean) {
    setRoleIds((current) =>
      checked ? [...current, roleId] : current.filter((id) => id !== roleId),
    )
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        <FieldGroup>
          <DialogHeader>
            <DialogTitle>
              {t("accounts.rolesDialog.title", { username: account.username })}
            </DialogTitle>
            <DialogDescription>{t("accounts.rolesDialog.description")}</DialogDescription>
          </DialogHeader>
          <FormProblem error={save.error} />
          <FieldGroup className="gap-3">
            {roles.map((role) => {
              // A Role can only be given or taken away by someone who holds everything in it.
              const withinOwn = role.permissions.every(can)
              return (
                <CheckboxField
                  key={role.id}
                  label={roleName(t, role)}
                  hint={withinOwn ? undefined : t("accounts.rolesDialog.beyondOwn")}
                  checked={roleIds.includes(role.id)}
                  disabled={!withinOwn}
                  onChange={(checked) => toggle(role.id, checked)}
                />
              )
            })}
          </FieldGroup>
          <DialogFooter>
            <DialogClose render={<Button variant="outline" />}>{t("common.cancel")}</DialogClose>
            <Button
              disabled={save.isPending}
              onClick={() =>
                save.mutate({ params: { path: { id: account.id } }, body: { roleIds } })
              }
            >
              {t("accounts.rolesDialog.submit")}
            </Button>
          </DialogFooter>
        </FieldGroup>
      </DialogContent>
    </Dialog>
  )
}
