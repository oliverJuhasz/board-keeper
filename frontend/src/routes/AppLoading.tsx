import { useTranslation } from "react-i18next"

/** What the app shows before it can show a page: while it asks who is signed in, or loads the first page's code. */
export function AppLoading() {
  const { t } = useTranslation()
  return <p className="p-8 text-center text-muted-foreground">{t("app.loading")}</p>
}
