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
      <DialogContent data-testid="account-roles-dialog">
        <FieldGroup>
          <DialogHeader>
            <DialogTitle>
              {t("accounts.rolesDialog.title", { username: account.username })}
            </DialogTitle>
            <DialogDescription>{t("accounts.rolesDialog.description")}</DialogDescription>
          </DialogHeader>
          <FormProblem error={save.error} />
          <FieldGroup className="gap-3">
            {roles.map((role) => (
              <CheckboxField
                key={role.id}
                label={roleName(t, role)}
                testId={`account-role-${role.id}`}
                checked={roleIds.includes(role.id)}
                onChange={(checked) => toggle(role.id, checked)}
              />
            ))}
          </FieldGroup>
          <DialogFooter>
            <DialogClose render={<Button variant="outline" />}>{t("common.cancel")}</DialogClose>
            <Button
              disabled={save.isPending}
              onClick={() =>
                save.mutate({ params: { path: { id: account.id } }, body: { roleIds } })
              }
              data-testid="save-roles"
            >
              {t("accounts.rolesDialog.submit")}
            </Button>
          </DialogFooter>
        </FieldGroup>
      </DialogContent>
    </Dialog>
  )
}
