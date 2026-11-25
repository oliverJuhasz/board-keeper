import { useQueryClient } from "@tanstack/react-query"
import { EllipsisIcon, PlusIcon } from "lucide-react"
import { useRef, useState } from "react"
import { useTranslation } from "react-i18next"
import { api, type Invite, type Player } from "@/api/client"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { LoadingRows } from "@/components/LoadingRows"
import { QuietLink } from "@/components/QuietLink"
import { useCan, useCurrentAccount } from "@/features/session/useSession"
import { InviteDialog } from "./InviteDialog"
import { PlayerNameDialog } from "./PlayerNameDialog"
import { RemovePlayerDialog } from "./RemovePlayerDialog"
import { ResetLinkDialog } from "./ResetLinkDialog"

/** Which dialog is open, and for which Player. */
type OpenDialog =
  | { kind: "add" }
  | { kind: "rename" | "invite" | "resetPassword" | "remove"; player: Player }
  | undefined

export function PlayersPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const can = useCan()
  const account = useCurrentAccount()
  const [dialog, setDialog] = useState<OpenDialog>()
  // The button that opens the dialog for a new Player, to give focus back to once it closes.
  const addButton = useRef<HTMLButtonElement>(null)

  const players = api.useQuery("get", "/api/players")
  const claims = api.useQuery("get", "/api/claims")
  // Only someone who may manage Invites is allowed to ask for them.
  const invites = api.useQuery("get", "/api/invites", {}, { enabled: can("MANAGE_INVITES") })

  const cancelInvite = api.useMutation("delete", "/api/invites/{id}", {
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["get", "/api/invites"] }),
  })

  const claimed = new Set(claims.data?.playerIds)
  const inviteOf = new Map(invites.data?.map((invite) => [invite.playerId, invite]))
  const close = () => setDialog(undefined)

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div className="space-y-1">
          <h1 className="font-heading text-xl font-semibold" data-testid="page-title">
            {t("players.title")}
          </h1>
          <p className="text-muted-foreground">{t("players.description")}</p>
        </div>
        {can("CREATE_PLAYERS") && (
          <Button
            ref={addButton}
            onClick={() => setDialog({ kind: "add" })}
            data-testid="add-player"
          >
            <PlusIcon />
            {t("players.add")}
          </Button>
        )}
      </div>

      {players.isPending ? (
        <LoadingRows label={t("players.loading")} />
      ) : players.isError ? (
        <Alert variant="destructive" data-testid="load-failed">
          <AlertTitle>{t("players.loadFailed")}</AlertTitle>
          <AlertDescription>{t("common.reloadToRetry")}</AlertDescription>
        </Alert>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t("players.name")}</TableHead>
              <TableHead>{t("players.status")}</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            {players.data.map((player) => {
              const invite = inviteOf.get(player.id)
              const hasAccount = claimed.has(player.id)
              return (
                <TableRow key={player.id} data-testid="player-row">
                  <TableCell className="font-medium">
                    {/* The name leads to the Player's statistics. */}
                    <QuietLink to={`/players/${player.id}`}>{player.name}</QuietLink>
                    {player.id === account.playerId && (
                      <Badge variant="outline" className="ml-2" data-testid="you">
                        {t("players.you")}
                      </Badge>
                    )}
                  </TableCell>
                  <TableCell>
                    <PlayerStatus hasAccount={hasAccount} invite={invite} />
                  </TableCell>
                  <TableCell className="text-right">
                    <PlayerActions
                      player={player}
                      hasAccount={hasAccount}
                      invite={invite}
                      onOpen={setDialog}
                      onCancelInvite={(id) => cancelInvite.mutate({ params: { path: { id } } })}
                    />
                  </TableCell>
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      )}

      {dialog?.kind === "add" && <PlayerNameDialog onClose={close} finalFocus={addButton} />}
      {dialog?.kind === "rename" && <PlayerNameDialog player={dialog.player} onClose={close} />}
      {dialog?.kind === "invite" && <InviteDialog player={dialog.player} onClose={close} />}
      {dialog?.kind === "resetPassword" && (
        <ResetLinkDialog player={dialog.player} onClose={close} />
      )}
      {dialog?.kind === "remove" && <RemovePlayerDialog player={dialog.player} onClose={close} />}
    </div>
  )
}

function PlayerStatus({ hasAccount, invite }: { hasAccount: boolean; invite?: Invite }) {
  const { t } = useTranslation()
  if (hasAccount) {
    return (
      <Badge variant="secondary" data-testid="has-account">
        {t("players.hasAccount")}
      </Badge>
    )
  }
  if (invite) {
    return (
      <Badge variant="outline" data-testid="invited">
        {invite.expired ? t("players.inviteExpired") : t("players.invited")}
      </Badge>
    )
  }
  return null
}

/** The "…" menu of one Player, holding only what the signed-in Account may do. */
function PlayerActions({
  player,
  hasAccount,
  invite,
  onOpen,
  onCancelInvite,
}: {
  player: Player
  hasAccount: boolean
  invite?: Invite
  onOpen: (dialog: OpenDialog) => void
  onCancelInvite: (inviteId: string) => void
}) {
  const { t } = useTranslation()
  const can = useCan()

  const mayInvite = can("MANAGE_INVITES") && !hasAccount
  const mayResetPassword = can("MANAGE_INVITES") && hasAccount
  const mayRemove = can("REMOVE_PLAYERS") && !hasAccount
  if (!can("EDIT_PLAYERS") && !mayInvite && !mayResetPassword && !mayRemove) {
    return null
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={
          <Button
            variant="ghost"
            size="icon-sm"
            aria-label={t("players.actions", { name: player.name })}
            data-testid="player-actions"
          />
        }
      >
        <EllipsisIcon />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-auto min-w-40">
        {can("EDIT_PLAYERS") && (
          <DropdownMenuItem
            onClick={() => onOpen({ kind: "rename", player })}
            data-testid="rename-player"
          >
            {t("players.rename")}
          </DropdownMenuItem>
        )}
        {mayInvite && (
          <DropdownMenuItem onClick={() => onOpen({ kind: "invite", player })} data-testid="invite">
            {invite ? t("players.inviteAgain") : t("players.invite")}
          </DropdownMenuItem>
        )}
        {mayInvite && invite && (
          <DropdownMenuItem onClick={() => onCancelInvite(invite.id)} data-testid="cancel-invite">
            {t("players.cancelInvite")}
          </DropdownMenuItem>
        )}
        {mayResetPassword && (
          <DropdownMenuItem
            onClick={() => onOpen({ kind: "resetPassword", player })}
            data-testid="create-reset-link"
          >
            {t("players.resetPassword")}
          </DropdownMenuItem>
        )}
        {mayRemove && (
          <DropdownMenuItem
            variant="destructive"
            onClick={() => onOpen({ kind: "remove", player })}
            data-testid="remove-player"
          >
            {t("players.remove")}
          </DropdownMenuItem>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
