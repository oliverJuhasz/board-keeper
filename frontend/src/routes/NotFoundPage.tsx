import { useTranslation } from "react-i18next"
import { Link } from "react-router"

export function NotFoundPage() {
  const { t } = useTranslation()

  return (
    <div className="space-y-2">
      <h1 className="font-heading text-xl font-semibold">{t("notFound.title")}</h1>
      <p className="text-muted-foreground">
        {t("notFound.text")}{" "}
        <Link to="/" className="text-foreground underline underline-offset-4">
          {t("notFound.goToStart")}
        </Link>
      </p>
    </div>
  )
}
