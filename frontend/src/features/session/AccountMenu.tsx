import { useQueryClient } from "@tanstack/react-query"
import { CircleUserRoundIcon } from "lucide-react"
import { useTranslation } from "react-i18next"
import { api, sessionKey } from "@/api/client"
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
import { useSession } from "./useSession"

export function AccountMenu() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const account = useSession().data?.account

  const signOut = api.useMutation("delete", "/api/session", {
    onSuccess: () => {
      // Record that nobody is signed in, which sends the app to the sign-in page. Then forget
      // everything that was loaded for this Account.
      queryClient.setQueryData(sessionKey, {})
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
        <DropdownMenuItem onClick={() => signOut.mutate({})}>
          {t("account.signOut")}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
