import { api } from "@/api/client"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { LoadingRows } from "./LoadingRows"

export function GameModulesCard() {
  const gameModules = api.useQuery("get", "/api/game-modules")

  return (
    <Card>
      <CardHeader>
        <CardTitle>Game modules</CardTitle>
        <CardDescription>The scoring support built into this installation.</CardDescription>
      </CardHeader>
      <CardContent>
        {gameModules.isPending ? (
          <LoadingRows label="Loading game modules" />
        ) : gameModules.isError ? (
          <Alert variant="destructive">
            <AlertTitle>Couldn't load the game modules</AlertTitle>
            <AlertDescription>Reload the page to try again.</AlertDescription>
          </Alert>
        ) : (
          <ul className="flex flex-wrap gap-2">
            {gameModules.data.map((gameModule) => (
              <li key={gameModule.id}>
                <Badge variant="secondary">{gameModule.key}</Badge>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}
