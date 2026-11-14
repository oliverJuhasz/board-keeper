import type { ReactNode } from "react"
import { useTranslation } from "react-i18next"
import { Navigate, useLocation } from "react-router"
import { PageMessage } from "@/components/PageMessage"
import { Button } from "@/components/ui/button"
import { AppLoading } from "@/routes/AppLoading"
import { useSession } from "./useSession"

/**
 * Draws its content only for a signed-in Account. Anyone else is sent to the sign-in page,
 * which sends them back to where they were going once they have signed in.
 */
export function RequireSignedIn({ children }: { children: ReactNode }) {
  const { t } = useTranslation()
  const session = useSession()
  const location = useLocation()

  // Without a connection, who is signed in comes from what the device kept (persistence.ts),
  // even when asking the server again failed.
  const unreachable = session.isError || (session.isPending && session.fetchStatus === "paused")
  if (session.isPending && !unreachable) {
    return <AppLoading />
  }
  if (!session.data) {
    return (
      <div className="mx-auto max-w-sm p-8">
        <PageMessage title={t("app.unreachable")}>
          <Button variant="outline" onClick={() => void session.refetch()}>
            {t("app.tryAgain")}
          </Button>
        </PageMessage>
      </div>
    )
  }
  if (!session.data.account) {
    return <Navigate to="/sign-in" replace state={{ from: location.pathname + location.search }} />
  }
  return children
}

/** The opposite: for pages that make no sense once signed in, such as the sign-in page. */
export function RedirectIfSignedIn({ children }: { children: ReactNode }) {
  const session = useSession()
  const location = useLocation()

  if (session.data?.account) {
    const from = (location.state as { from?: string } | null)?.from
    return <Navigate to={from ?? "/"} replace />
  }
  return children
}
