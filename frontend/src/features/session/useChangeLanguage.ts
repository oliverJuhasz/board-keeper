import { useQueryClient } from "@tanstack/react-query"
import { useEffect } from "react"
import { api, sessionKey, type Session } from "@/api/client"
import { currentLanguage, setLanguage, type LanguageCode } from "@/i18n"
import { useSession } from "./useSession"

/**
 * Switches the interface to a language. For a signed-in Account the choice is also saved on
 * the Account, so it follows the person to their other devices.
 */
export function useChangeLanguage() {
  const queryClient = useQueryClient()
  const session = useSession()
  const saveOnAccount = api.useMutation("put", "/api/account/language")

  return (language: LanguageCode) => {
    void setLanguage(language)
    if (session.data?.account) {
      queryClient.setQueryData<Session>(sessionKey, (current) =>
        current?.account ? { account: { ...current.account, language } } : current,
      )
      saveOnAccount.mutate({ body: { language } })
    }
  }
}

/** After signing in, shows the interface in the language saved on the Account. */
export function useAccountLanguage() {
  const language = useSession().data?.account?.language

  // useEffect runs after drawing, whenever `language` has changed: here, when someone signs in.
  useEffect(() => {
    if (language && language !== currentLanguage()) {
      void setLanguage(language)
    }
  }, [language])
}
