import { useQueryClient } from "@tanstack/react-query"
import { CircleUserRoundIcon } from "lucide-react"
import { useTranslation } from "react-i18next"
import { Link } from "react-router"
import { api, sessionKey, type Session } from "@/api/client"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { useCan, useSession } from "./useSession"

export function AccountMenu() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const account = useSession().data?.account
  const can = useCan()
  // Where the source code of the running version is. It changes only with a new version.
  const about = api.useQuery("get", "/api/about", {}, { staleTime: Infinity })

  const signOut = api.useMutation("delete", "/api/session", {
    onSuccess: () => {
      // Record that nobody is signed in, which sends the app to the sign-in page. Whether the
      // installation offers passkeys stays as it was. Then forget everything that was loaded
      // for this Account.
      queryClient.setQueryData<Session>(sessionKey, (current) => ({
        passkeys: current?.passkeys ?? false,
      }))
      queryClient.removeQueries({ predicate: (query) => query.queryKey[1] !== "/api/session" })
    },
  })

  // For the moment between signing out and the sign-in page appearing.
  if (!account) {
    return null
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={<Button variant="ghost" size="icon" aria-label={t("account.menu")} />}
      >
        <CircleUserRoundIcon />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-auto min-w-48">
        <DropdownMenuGroup>
          <DropdownMenuLabel>
            <div className="text-foreground">{account.playerName}</div>
            <div>{t("account.signedInAs", { username: account.username })}</div>
          </DropdownMenuLabel>
        </DropdownMenuGroup>
        <DropdownMenuSeparator />
        {/* `render` makes the menu item a link, so it works like any other link in the app. */}
        <DropdownMenuItem render={<Link to="/settings" />}>
          {t("account.settings")}
        </DropdownMenuItem>
        {can("MANAGE_ACCOUNTS") && (
          <DropdownMenuItem render={<Link to="/accounts" />}>
            {t("account.accounts")}
          </DropdownMenuItem>
        )}
        {can("MANAGE_ROLES") && (
          <DropdownMenuItem render={<Link to="/roles" />}>{t("account.roles")}</DropdownMenuItem>
        )}
        {about.data && (
          // Board Keeper's licence, the AGPL, asks that everyone using it can get its source (ADR 0016).
          <DropdownMenuItem
            render={<a href={about.data.sourceCode} target="_blank" rel="noreferrer" />}
          >
            {t("account.sourceCode")}
          </DropdownMenuItem>
        )}
        <DropdownMenuSeparator />
        <DropdownMenuItem onClick={() => signOut.mutate({})}>
          {t("account.signOut")}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
