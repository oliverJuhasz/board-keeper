import { useTranslation } from "react-i18next"
import { api } from "@/api/client"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { LoadingRows } from "./LoadingRows"

export function GamesCard() {
  const { t } = useTranslation()
  // Loads the Games when the card first appears, and tells us how the request is going.
  const games = api.useQuery("get", "/api/games")

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("games.title")}</CardTitle>
        <CardDescription>{t("games.description")}</CardDescription>
      </CardHeader>
      <CardContent>
        {games.isPending ? (
          <LoadingRows label={t("games.loading")} />
        ) : games.isError ? (
          <Alert variant="destructive">
            <AlertTitle>{t("games.loadFailed")}</AlertTitle>
            <AlertDescription>{t("common.reloadToRetry")}</AlertDescription>
          </Alert>
        ) : games.data.length === 0 ? (
          <p className="text-muted-foreground">{t("games.empty")}</p>
        ) : (
          <ul className="space-y-1">
            {games.data.map((game) => (
              <li key={game.id}>{game.name}</li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}
