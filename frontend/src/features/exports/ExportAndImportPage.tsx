import { useTranslation } from "react-i18next"
import { PageHeader } from "@/components/PageHeader"
import { useCan } from "@/features/session/useSession"
import { NotFoundPage } from "@/routes/NotFoundPage"
import { ExportCard } from "./ExportCard"

/**
 * Moving the Games, Players and Plays to another Installation: an Export, and later an Import.
 * Both are for Admins only, so anyone else finds no page here at all.
 */
export function ExportAndImportPage() {
  const { t } = useTranslation()
  const can = useCan()

  if (!can("EXPORT")) {
    return <NotFoundPage />
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title={t("exportAndImport.title")}
        description={t("exportAndImport.description")}
      />
      <ExportCard />
    </div>
  )
}
