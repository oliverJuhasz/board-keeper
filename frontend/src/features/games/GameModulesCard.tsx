import { useTranslation } from "react-i18next"
import { api } from "@/api/client"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { LoadingRows } from "./LoadingRows"
import { useGameModuleTexts } from "./useGameModuleTexts"

export function GameModulesCard() {
  const { t } = useTranslation()
  const gameModules = api.useQuery("get", "/api/game-modules")
  const gameModuleText = useGameModuleTexts()

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("gameModules.title")}</CardTitle>
        <CardDescription>{t("gameModules.description")}</CardDescription>
      </CardHeader>
      <CardContent>
        {gameModules.isPending ? (
          <LoadingRows label={t("gameModules.loading")} />
        ) : gameModules.isError ? (
          <Alert variant="destructive">
            <AlertTitle>{t("gameModules.loadFailed")}</AlertTitle>
            <AlertDescription>{t("common.reloadToRetry")}</AlertDescription>
          </Alert>
        ) : (
          <ul className="flex flex-wrap gap-2">
            {gameModules.data.map((gameModule) => (
              <li key={gameModule.id}>
                <Badge variant="secondary">
                  {gameModuleText(`${gameModule.key}.name`, gameModule.key)}
                </Badge>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}
