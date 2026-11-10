import { zodResolver } from "@hookform/resolvers/zod"
import { useQueryClient } from "@tanstack/react-query"
import { useState } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { z } from "zod"
import { api, type Permission, type Role } from "@/api/client"
import { CheckboxField } from "@/components/CheckboxField"
import { FormProblem } from "@/components/FormProblem"
import { TextField } from "@/components/TextField"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { FieldGroup, FieldLegend, FieldSet } from "@/components/ui/field"
import { playerName } from "@/features/session/accountRules"
import { useCan } from "@/features/session/useSession"
import { allPermissions } from "./permissions"
import { roleName } from "./roleName"

// A Role's name follows the same rule as a Player's: 1 to 50 characters.
const roleForm = z.object({ name: playerName })

/** The dialog for a Role: adds one, or changes the one it is given. */
export function RoleDialog({ role, onClose }: { role?: Role; onClose: () => void }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const can = useCan()
  const form = useForm({
    resolver: zodResolver(roleForm),
    defaultValues: { name: role ? roleName(t, role) : "" },
  })
  // The ticked Permissions are kept in ordinary React state, next to the form's name field.
  const [permissions, setPermissions] = useState<Permission[]>(role?.permissions ?? [])

  const onSuccess = () => {
    void queryClient.invalidateQueries({ queryKey: ["get", "/api/roles"] })
    onClose()
  }
  const create = api.useMutation("post", "/api/roles", { onSuccess })
  const update = api.useMutation("put", "/api/roles/{id}", { onSuccess })

  function toggle(permission: Permission, checked: boolean) {
    setPermissions((current) =>
      checked ? [...current, permission] : current.filter((held) => held !== permission),
    )
  }

  const submit = form.handleSubmit(({ name }) => {
    const body = { name, permissions }
    if (role) {
      update.mutate({ params: { path: { id: role.id } }, body })
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
                {role
                  ? t("roles.dialog.editTitle", { name: roleName(t, role) })
                  : t("roles.dialog.addTitle")}
              </DialogTitle>
            </DialogHeader>
            <FormProblem error={create.error ?? update.error} />
            <TextField
              label={t("roles.name")}
              autoComplete="off"
              // The Member Role keeps its name, which the interface translates.
              disabled={role?.builtIn !== undefined}
              error={form.formState.errors.name}
              {...form.register("name")}
            />
            <FieldSet>
              <FieldLegend variant="label">{t("roles.permissions")}</FieldLegend>
              <FieldGroup className="gap-3">
                {allPermissions.map((permission) => (
                  <CheckboxField
                    key={permission}
                    label={t(`permissions.${permission}.name`)}
                    // Nobody can hand out a Permission they don't hold themselves.
                    hint={
                      can(permission)
                        ? t(`permissions.${permission}.hint`)
                        : t("roles.dialog.notYours")
                    }
                    checked={permissions.includes(permission)}
                    disabled={!can(permission)}
                    onChange={(checked) => toggle(permission, checked)}
                  />
                ))}
              </FieldGroup>
            </FieldSet>
            <DialogFooter>
              <DialogClose render={<Button type="button" variant="outline" />}>
                {t("common.cancel")}
              </DialogClose>
              <Button type="submit" disabled={create.isPending || update.isPending}>
                {role ? t("roles.dialog.submitEdit") : t("roles.dialog.submitAdd")}
              </Button>
            </DialogFooter>
          </FieldGroup>
        </form>
      </DialogContent>
    </Dialog>
  )
}
