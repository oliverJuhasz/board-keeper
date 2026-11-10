import { api } from "@/api/client"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { LoadingRows } from "./LoadingRows"

export function GamesCard() {
  // Loads the Games when the card first appears, and tells us how the request is going.
  const games = api.useQuery("get", "/api/games")

  return (
    <Card>
      <CardHeader>
        <CardTitle>Games</CardTitle>
        <CardDescription>The games you can record plays of.</CardDescription>
      </CardHeader>
      <CardContent>
        {games.isPending ? (
          <LoadingRows label="Loading games" />
        ) : games.isError ? (
          <Alert variant="destructive">
            <AlertTitle>Couldn't load the games</AlertTitle>
            <AlertDescription>Reload the page to try again.</AlertDescription>
          </Alert>
        ) : games.data.length === 0 ? (
          <p className="text-muted-foreground">No games yet.</p>
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
