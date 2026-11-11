import { useQueryClient } from "@tanstack/react-query"
import { PlusIcon } from "lucide-react"
import { useState } from "react"
import { useTranslation } from "react-i18next"
import { api, type Role } from "@/api/client"
import { ActionsMenu } from "@/components/ActionsMenu"
import { ConfirmDialog } from "@/components/ConfirmDialog"
import { PageHeader } from "@/components/PageHeader"
import { QueryState } from "@/components/QueryState"
import { Button } from "@/components/ui/button"
import { DropdownMenuItem } from "@/components/ui/dropdown-menu"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { assignablePermissions } from "./permissions"
import { RoleDialog } from "./RoleDialog"
import { roleName } from "./roleName"

type OpenDialog = { kind: "add" } | { kind: "edit" | "delete"; role: Role } | undefined

export function RolesPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [dialog, setDialog] = useState<OpenDialog>()
  const roles = api.useQuery("get", "/api/roles")
  const close = () => setDialog(undefined)

  const deleteRole = api.useMutation("delete", "/api/roles/{id}", {
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["get", "/api/roles"] })
      void queryClient.invalidateQueries({ queryKey: ["get", "/api/accounts"] })
      close()
    },
  })

  /** What a Role may do, in words. */
  function permissionsOf(role: Role) {
    if (role.builtIn === "ADMIN") {
      return t("roles.allPermissions")
    }
    if (role.permissions.length === 0) {
      return t("roles.noPermissions")
    }
    return assignablePermissions
      .filter((permission) => role.permissions.includes(permission))
      .map((permission) => t(`permissions.${permission}.name`))
      .join(", ")
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title={t("roles.title")}
        description={t("roles.description")}
        action={
          <Button onClick={() => setDialog({ kind: "add" })}>
            <PlusIcon />
            {t("roles.add")}
          </Button>
        }
      />

      <QueryState queries={[roles]} loading={t("roles.loading")} failed={t("roles.loadFailed")}>
        {() => (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t("roles.name")}</TableHead>
                <TableHead>{t("roles.permissions")}</TableHead>
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {roles.data?.map((role) => (
                <TableRow key={role.id}>
                  <TableCell className="font-medium">{roleName(t, role)}</TableCell>
                  <TableCell className="whitespace-normal text-muted-foreground">
                    {permissionsOf(role)}
                  </TableCell>
                  <TableCell className="text-right">
                    {/* The Admin Role can't be changed at all. */}
                    {role.builtIn !== "ADMIN" && (
                      <ActionsMenu label={t("roles.actions", { name: roleName(t, role) })}>
                        <DropdownMenuItem onClick={() => setDialog({ kind: "edit", role })}>
                          {t("roles.edit")}
                        </DropdownMenuItem>
                        {!role.builtIn && (
                          <DropdownMenuItem
                            variant="destructive"
                            onClick={() => setDialog({ kind: "delete", role })}
                          >
                            {t("roles.delete")}
                          </DropdownMenuItem>
                        )}
                      </ActionsMenu>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </QueryState>

      {dialog?.kind === "add" && <RoleDialog onClose={close} />}
      {dialog?.kind === "edit" && <RoleDialog role={dialog.role} onClose={close} />}
      {dialog?.kind === "delete" && (
        <ConfirmDialog
          title={t("roles.deleteDialog.title", { name: roleName(t, dialog.role) })}
          description={t("roles.deleteDialog.description")}
          confirm={t("roles.deleteDialog.confirm")}
          onConfirm={() => deleteRole.mutate({ params: { path: { id: dialog.role.id } } })}
          pending={deleteRole.isPending}
          error={deleteRole.error}
          onClose={close}
        />
      )}
    </div>
  )
}
