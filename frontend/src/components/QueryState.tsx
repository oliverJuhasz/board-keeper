import type { ReactNode } from "react"
import { useTranslation } from "react-i18next"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { LoadingRows } from "@/components/LoadingRows"

/**
 * Draws its content once everything it needs has loaded. Until then it shows placeholder rows,
 * and if loading failed, a message. Pass the TanStack Query results the content depends on.
 */
export function QueryState({
  queries,
  loading,
  failed,
  children,
}: {
  queries: { isPending: boolean; isError: boolean }[]
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
  if (queries.some((query) => query.isPending)) {
    return <LoadingRows label={loading} />
  }
  return children()
}
