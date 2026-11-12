import { useQueryClient } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { Link, useNavigate, useSearchParams } from "react-router"
import { api, type Game, type Player } from "@/api/client"
import { PageMessage } from "@/components/PageMessage"
import { QueryState } from "@/components/QueryState"
import { useCurrentAccount } from "@/features/session/useSession"
import { PlayEntryForm } from "./PlayEntryForm"
import { blankPlay } from "./playForm"
import { playKey, playsKey } from "./playQueries"

/** Records a Play of the Game named in the address: /plays/new?game=<id>. */
export function RecordPlayPage() {
  const { t } = useTranslation()
  // The part of the address after the "?".
  const [searchParams] = useSearchParams()
  const gameId = searchParams.get("game")
  const games = api.useQuery("get", "/api/games")
  const players = api.useQuery("get", "/api/players")

  return (
    <QueryState
      queries={[games, players]}
      loading={t("plays.record.loading")}
      failed={t("plays.record.loadFailed")}
    >
      {() => {
        const game = games.data?.find((candidate) => candidate.id === gameId)
        if (!game) {
          return (
            <PageMessage title={t("plays.record.noGame.title")}>
              <p>{t("plays.record.noGame.text")}</p>
              <Link to="/" className="text-foreground underline underline-offset-4">
                {t("plays.record.noGame.link")}
              </Link>
            </PageMessage>
          )
        }
        return <RecordPlay game={game} players={players.data ?? []} />
      }}
    </QueryState>
  )
}

function RecordPlay({ game, players }: { game: Game; players: Player[] }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const account = useCurrentAccount()

  const record = api.useMutation("post", "/api/plays", {
    onSuccess: (play) => {
      // The answer is the recorded Play. Keeping it lets the Play's page show it at once,
      // without a moment of "loading" first.
      queryClient.setQueryData(playKey(play.id), play)
      // The play history that was fetched earlier is out of date now. Throwing it away makes
      // the Plays page load it afresh, instead of showing the old list for a moment.
      queryClient.removeQueries({ queryKey: playsKey })
      void navigate(`/plays/${play.id}`)
    },
  })
  // Whoever records a Play has usually played in it.
  const recorder = players.find((player) => player.id === account.playerId)

  return (
    <PlayEntryForm
      title={t("plays.record.title")}
      description={t("plays.record.description", { game: game.name })}
      gameId={game.id}
      players={players}
      initial={blankPlay(game, recorder?.id)}
      submitLabel={t("plays.record.submit")}
      cancelTo="/"
      pending={record.isPending}
      error={record.error}
      onSubmit={(body) => record.mutate({ body })}
    />
  )
}
