import { useQueryClient } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { Link, useNavigate, useSearchParams } from "react-router"
import { api, type Game, type GameModule, type Player } from "@/api/client"
import { PageMessage } from "@/components/PageMessage"
import { QueryState } from "@/components/QueryState"
import { moduleWithId, useGameModules, useGameName } from "@/features/games/useGameModules"
import { useGameModuleTextsQuery } from "@/features/games/useGameModuleTexts"
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
  // The form is drawn from the score pad of the Game's Game Module, in the module's words.
  const modules = useGameModules()
  const texts = useGameModuleTextsQuery()

  return (
    <QueryState
      queries={[games, players, modules, texts]}
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
        const module = moduleWithId(modules.data, game.gameModuleId)
        if (!module) {
          // The Game's module is not part of this build of Board Keeper.
          return <PageMessage title={t("problems.plays.gameModuleMissing")} />
        }
        return <RecordPlay game={game} module={module} players={players.data ?? []} />
      }}
    </QueryState>
  )
}

function RecordPlay({
  game,
  module,
  players,
}: {
  game: Game
  module: GameModule
  players: Player[]
}) {
  const { t } = useTranslation()
  const gameName = useGameName()
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
      description={t("plays.record.description", { game: gameName(game) })}
      gameId={game.id}
      module={module}
      players={players}
      initial={blankPlay(module, game, recorder?.id)}
      submitLabel={t("plays.record.submit")}
      cancelTo="/"
      pending={record.isPending}
      error={record.error}
      onSubmit={(body) => record.mutate({ body })}
    />
  )
}
