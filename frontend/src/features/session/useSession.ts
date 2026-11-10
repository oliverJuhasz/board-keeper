import { api, type CurrentAccount, type Permission } from "@/api/client"

/** Asks the server who is signed in. Every part of the app that needs to know uses this. */
export function useSession() {
  // The answer is trusted for half a minute, so the many components that use this hook don't
  // each ask again. Signing in and out update it directly.
  return api.useQuery("get", "/api/session", {}, { staleTime: 30_000 })
}

/**
 * The signed-in Account. Only for components inside the signed-in part of the app, which
 * RequireSignedIn doesn't draw until the Account is known.
 */
export function useCurrentAccount(): CurrentAccount {
  const account = useSession().data?.account
  if (!account) {
    throw new Error("useCurrentAccount was used outside the signed-in part of the app")
  }
  return account
}

/** Whether the signed-in Account holds a Permission, for showing or hiding what needs it. */
export function useCan() {
  const permissions = useSession().data?.account?.permissions ?? []
  return (permission: Permission) => permissions.includes(permission)
}
