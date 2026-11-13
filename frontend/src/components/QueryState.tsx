import type { ReactNode } from "react"
import { useTranslation } from "react-i18next"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { LoadingRows } from "@/components/LoadingRows"

/**
 * Draws its content once everything it needs has loaded. Until then it shows placeholder rows,
 * and if loading failed, a message. Pass the TanStack Query results the content depends on.
 *
 * Without a connection, TanStack Query doesn't fail a query that has nothing kept on the device:
 * it pauses it ("fetchStatus: paused") and goes on as soon as there is a connection again. That
 * gets a message of its own, since the content then appears by itself.
 */
export function QueryState({
  queries,
  loading,
  failed,
  children,
}: {
  queries: { isPending: boolean; isError: boolean; fetchStatus?: string }[]
  /** What a screen reader announces while loading, such as "Loading roles". */
  loading: string
  /** The title of the message if loading failed. */
  failed: string
  children: () => ReactNode
}) {
  const { t } = useTranslation()

  if (queries.some((query) => query.isError)) {
    return (
      <Alert variant="destructive">
        <AlertTitle>{failed}</AlertTitle>
        <AlertDescription>{t("common.reloadToRetry")}</AlertDescription>
      </Alert>
    )
  }
  if (queries.some((query) => query.isPending && query.fetchStatus === "paused")) {
    return (
      <Alert>
        <AlertTitle>{t("common.offline.title")}</AlertTitle>
        <AlertDescription>{t("common.offline.text")}</AlertDescription>
      </Alert>
    )
  }
  if (queries.some((query) => query.isPending)) {
    return <LoadingRows label={loading} />
  }
  return children()
}
