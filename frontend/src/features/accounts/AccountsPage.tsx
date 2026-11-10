import { useQueryClient } from "@tanstack/react-query"
import { useState } from "react"
import { useTranslation } from "react-i18next"
import { api, type Account } from "@/api/client"
import { ActionsMenu } from "@/components/ActionsMenu"
import { ConfirmDialog } from "@/components/ConfirmDialog"
import { PageHeader } from "@/components/PageHeader"
import { QueryState } from "@/components/QueryState"
import { Badge } from "@/components/ui/badge"
import { DropdownMenuItem } from "@/components/ui/dropdown-menu"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { roleName } from "@/features/roles/roleName"
import { useCurrentAccount } from "@/features/session/useSession"
import { AccountRolesDialog } from "./AccountRolesDialog"

type OpenDialog = { kind: "roles" | "disable"; account: Account } | undefined

export function AccountsPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const me = useCurrentAccount()
  const [dialog, setDialog] = useState<OpenDialog>()
  const close = () => setDialog(undefined)

  // An Account refers to its Player and its Roles by id; their names come from two more lists.
  const accounts = api.useQuery("get", "/api/accounts")
  const players = api.useQuery("get", "/api/players")
  const roles = api.useQuery("get", "/api/roles")

  const setDisabled = api.useMutation("put", "/api/accounts/{id}/disabled", {
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["get", "/api/accounts"] })
      close()
    },
  })

  const playerName = (id: string) => players.data?.find((player) => player.id === id)?.name
  const rolesOf = (account: Account) =>
    (roles.data ?? []).filter((role) => account.roleIds.includes(role.id))

  return (
    <div className="space-y-6">
      <PageHeader title={t("accounts.title")} description={t("accounts.description")} />

      <QueryState
        queries={[accounts, players, roles]}
        loading={t("accounts.loading")}
        failed={t("accounts.loadFailed")}
      >
        {() => (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t("accounts.username")}</TableHead>
                <TableHead>{t("accounts.player")}</TableHead>
                <TableHead>{t("accounts.roles")}</TableHead>
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {accounts.data?.map((account) => (
                <TableRow key={account.id}>
                  <TableCell className="font-medium">
                    {account.username}
                    {account.id === me.id && (
                      <Badge variant="outline" className="ml-2">
                        {t("accounts.you")}
                      </Badge>
                    )}
                    {account.disabled && (
                      <Badge variant="destructive" className="ml-2">
                        {t("accounts.disabled")}
                      </Badge>
                    )}
                  </TableCell>
                  <TableCell>{playerName(account.playerId)}</TableCell>
                  <TableCell>
                    <div className="flex flex-wrap gap-1">
                      {rolesOf(account).map((role) => (
                        <Badge key={role.id} variant="secondary">
                          {roleName(t, role)}
                        </Badge>
                      ))}
                      {account.roleIds.length === 0 && (
                        <span className="text-muted-foreground">{t("accounts.noRoles")}</span>
                      )}
                    </div>
                  </TableCell>
                  <TableCell className="text-right">
                    <ActionsMenu label={t("accounts.actions", { username: account.username })}>
                      <DropdownMenuItem onClick={() => setDialog({ kind: "roles", account })}>
                        {t("accounts.changeRoles")}
                      </DropdownMenuItem>
                      {/* Nobody can disable their own Account. */}
                      {account.id !== me.id &&
                        (account.disabled ? (
                          <DropdownMenuItem
                            onClick={() =>
                              setDisabled.mutate({
                                params: { path: { id: account.id } },
                                body: { disabled: false },
                              })
                            }
                          >
                            {t("accounts.enable")}
                          </DropdownMenuItem>
                        ) : (
                          <DropdownMenuItem
                            variant="destructive"
                            onClick={() => setDialog({ kind: "disable", account })}
                          >
                            {t("accounts.disable")}
                          </DropdownMenuItem>
                        ))}
                    </ActionsMenu>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </QueryState>

      {dialog?.kind === "roles" && (
        <AccountRolesDialog account={dialog.account} roles={roles.data ?? []} onClose={close} />
      )}
      {dialog?.kind === "disable" && (
        <ConfirmDialog
          title={t("accounts.disableDialog.title", { username: dialog.account.username })}
          description={t("accounts.disableDialog.description", {
            username: dialog.account.username,
          })}
          confirm={t("accounts.disableDialog.confirm")}
          onConfirm={() =>
            setDisabled.mutate({
              params: { path: { id: dialog.account.id } },
              body: { disabled: true },
            })
          }
          pending={setDisabled.isPending}
          error={setDisabled.error}
          onClose={close}
        />
      )}
    </div>
  )
}
