import { Trash2Icon } from "lucide-react"
import { useState } from "react"
import { useTranslation } from "react-i18next"
import { api } from "@/api/client"
import { explainProblem } from "@/api/problems"
import { ConfirmDialog } from "@/components/ConfirmDialog"
import { LinkButton } from "@/components/LinkButton"
import { Button } from "@/components/ui/button"
import { useGameName } from "@/features/games/useGameModules"
import { useGameModuleTexts } from "@/features/games/useGameModuleTexts"
import { useCurrentAccount } from "@/features/session/useSession"
import { formatDay } from "@/lib/dates"
import { forgetWaiting, useWaitingPlays, type WaitingPlay } from "./waitingPlays"

/**
 * The Plays recorded on this device that haven't reached Board Keeper yet, for the start page.
 * One that was refused, or needs a tie decided, can be opened to put it right.
 */
export function WaitingPlays() {
  const { t } = useTranslation()
  const account = useCurrentAccount()
  const waiting = useWaitingPlays(account.id)
  const games = api.useQuery("get", "/api/games")
  const players = api.useQuery("get", "/api/players")
  const gameName = useGameName()
  const moduleTexts = useGameModuleTexts()
  const [removing, setRemoving] = useState<WaitingPlay>()

  if (waiting.length === 0) {
    return null
  }
  const nameOfGame = (id: string) => {
    const game = games.data?.find((candidate) => candidate.id === id)
    return game ? gameName(game) : ""
  }
  const nameOfPlayer = (id: string) => players.data?.find((player) => player.id === id)?.name

  return (
    <section className="space-y-2">
      <div className="space-y-1">
        <h2 className="text-sm font-medium">{t("plays.waiting.title")}</h2>
        <p className="text-sm text-muted-foreground">{t("plays.waiting.description")}</p>
      </div>
      <ul className="divide-y rounded-md border">
        {waiting.map((item) => (
          <li key={item.play.id} className="flex items-center gap-3 p-3" data-testid="waiting-play">
            <div className="min-w-0 flex-1">
              <p className="font-medium">
                {nameOfGame(item.play.gameId)} · {formatDay(item.play.playedOn)}
              </p>
              <p className="truncate text-sm text-muted-foreground">
                {item.play.players
                  .map((player) => nameOfPlayer(player.playerId))
                  .filter(Boolean)
                  .join(", ")}
              </p>
              <p
                className={
                  item.problem ? "text-sm text-destructive" : "text-sm text-muted-foreground"
                }
              >
                {item.problem
                  ? explainProblem(t, item.problem, moduleTexts)
                  : t("plays.waiting.waiting")}
              </p>
            </div>
            {item.problem && (
              <LinkButton
                size="sm"
                to={`/plays/new?game=${item.play.gameId}&waiting=${item.play.id}`}
              >
                {t("plays.waiting.open")}
              </LinkButton>
            )}
            <Button
              variant="ghost"
              size="icon"
              aria-label={t("plays.waiting.remove", {
                game: nameOfGame(item.play.gameId),
                date: formatDay(item.play.playedOn),
              })}
              onClick={() => setRemoving(item)}
            >
              <Trash2Icon />
            </Button>
          </li>
        ))}
      </ul>
      {removing && (
        <ConfirmDialog
          title={t("plays.waiting.removeDialog.title")}
          description={t("plays.waiting.removeDialog.description")}
          confirm={t("plays.waiting.removeDialog.confirm")}
          onConfirm={() => {
            forgetWaiting(removing.play.id)
            setRemoving(undefined)
          }}
          pending={false}
          error={undefined}
          onClose={() => setRemoving(undefined)}
        />
      )}
    </section>
  )
}
