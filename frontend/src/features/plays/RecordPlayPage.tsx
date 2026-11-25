import { useQueryClient, type QueryClient } from "@tanstack/react-query"
import { useState } from "react"
import { useTranslation } from "react-i18next"
import { Link, useNavigate, useSearchParams } from "react-router"
import { api, type Game, type GameModule, type NewPlay, type Play, type Player } from "@/api/client"
import { PageMessage } from "@/components/PageMessage"
import { QueryState } from "@/components/QueryState"
import { moduleWithId, useGameModules, useGameName } from "@/features/games/useGameModules"
import { useGameModuleTextsQuery } from "@/features/games/useGameModuleTexts"
import { useCurrentAccount } from "@/features/session/useSession"
import { uuidv7 } from "@/lib/uuid"
import { PlayEntryForm } from "./PlayEntryForm"
import { anotherPlay, blankPlay, fromNewPlay, playAgain } from "./playForm"
import { playKey, playsKey } from "./playQueries"
import { forgetWaiting, keepWaiting, useWaitingPlay, type WaitingPlay } from "./waitingPlays"

/**
 * Records a Play of the Game named in the address: /plays/new?game=<id>. With &again=<id> of
 * an earlier Play, the form starts with that Play's Players and choices. With &another=<id>, it
 * starts with its date and location as well, for one more Play on the same day at the same
 * place: "Record another play" on a Play's page leads here. With &waiting=<id> of
 * a Play that waits on this device to be sent, the form starts with that Play, to put right
 * what kept it from being recorded.
 */
export function RecordPlayPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const account = useCurrentAccount()
  // The part of the address after the "?".
  const [searchParams] = useSearchParams()
  const gameId = searchParams.get("game")
  const anotherId = searchParams.get("another")
  // The earlier Play the form starts from, if there is one.
  const againId = anotherId ?? searchParams.get("again")
  const waiting = useWaitingPlay(account.id, searchParams.get("waiting"))
  const games = api.useQuery("get", "/api/games")
  // Without a connection, the Play to play again may still be in the history kept on the
  // device (persistence.ts): the start page offers it from there.
  const [inHistory] = useState(() => (againId ? playInHistory(queryClient, againId) : undefined))
  // The Play to play again, if there is one. A query that isn't "enabled" is never sent.
  const previous = api.useQuery(
    "get",
    "/api/plays/{id}",
    { params: { path: { id: againId ?? "" } } },
    { enabled: againId !== null && inHistory === undefined },
  )
  const players = api.useQuery("get", "/api/players")
  // The form is drawn from the score pad of the Game's Game Module, in the module's words.
  const modules = useGameModules()
  const texts = useGameModuleTextsQuery()
  const again = previous.data ?? inHistory

  return (
    <QueryState
      queries={[
        games,
        players,
        modules,
        texts,
        // Wait for the Play to play again. If it can't be loaded, the form simply starts empty.
        {
          isPending: againId !== null && !again && previous.fetchStatus === "fetching",
          isError: false,
        },
      ]}
      loading={t("plays.record.loading")}
      failed={t("plays.record.loadFailed")}
    >
      {() => {
        const game = games.data?.find(
          (candidate) => candidate.id === (waiting?.play.gameId ?? gameId),
        )
        if (!game) {
          return (
            <PageMessage title={t("plays.record.noGame.title")}>
              <p>{t("plays.record.noGame.text")}</p>
              <Link
                to="/"
                className="text-foreground underline underline-offset-4"
                data-testid="go-to-games"
              >
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
        return (
          <RecordPlay
            game={game}
            module={module}
            players={players.data ?? []}
            previous={again?.game.id === game.id ? again : undefined}
            another={anotherId !== null}
            waiting={waiting?.play.gameId === game.id ? waiting : undefined}
          />
        )
      }}
    </QueryState>
  )
}

/** A Play in the pages of the play history that the device has, if it is there. */
function playInHistory(queryClient: QueryClient, id: string): Play | undefined {
  return queryClient
    .getQueriesData<{ plays?: Play[] }>({ queryKey: playsKey })
    .flatMap(([, page]) => page?.plays ?? [])
    .find((play) => play.id === id)
}

function RecordPlay({
  game,
  module,
  players,
  previous,
  another,
  waiting,
}: {
  game: Game
  module: GameModule
  players: Player[]
  /** The Play to play again, whose Players and choices the form starts with. */
  previous?: Play
  /** Whether the new Play follows `previous` on the same day, at the same place. */
  another: boolean
  /** The Play that waits on this device, which the form starts with. */
  waiting?: WaitingPlay
}) {
  const { t } = useTranslation()
  const gameName = useGameName()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const account = useCurrentAccount()
  // The new Play's id, chosen here, so that a Play kept on the device is recorded once however
  // often it is sent. A waiting Play keeps its own, unless another Play has it already.
  const [id] = useState(() =>
    waiting && waiting.problem?.code !== "plays.idTaken" ? waiting.play.id : uuidv7(),
  )

  /** Keeps the Play on this device, to be sent once Board Keeper can be reached. */
  function keep(play: NewPlay) {
    if (waiting) {
      forgetWaiting(waiting.play.id)
    }
    keepWaiting({
      accountId: account.id,
      gameModuleId: module.id,
      play: { ...play, id },
      savedAt: new Date().toISOString(),
    })
    // The start page lists what waits to be sent.
    void navigate("/")
  }

  const record = api.useMutation("post", "/api/plays", {
    onSuccess: (play) => {
      if (waiting) {
        forgetWaiting(waiting.play.id)
      }
      // The answer is the recorded Play. Keeping it lets the Play's page show it at once,
      // without a moment of "loading" first.
      queryClient.setQueryData(playKey(play.id), play)
      // The play history that was fetched earlier is out of date now. Throwing it away makes
      // the Plays page load it afresh, instead of showing the old list for a moment.
      queryClient.removeQueries({ queryKey: playsKey })
      void navigate(`/plays/${play.id}`)
    },
    onError: (error, { body }) => {
      // fetch() fails with a TypeError when there is no answer at all: keep the Play.
      if (error instanceof TypeError) {
        keep(body)
      }
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
      initial={
        waiting
          ? fromNewPlay(module, waiting.play)
          : previous
            ? (another ? anotherPlay : playAgain)(
                module,
                game,
                previous,
                players.map((player) => player.id),
              )
            : blankPlay(module, game, recorder?.id)
      }
      submitLabel={t("plays.record.submit")}
      cancelTo="/"
      pending={record.isPending}
      // Why the Play waited, until it has been sent again. A tie needs no message: the form
      // asks about it, now that the server can be asked how the Play ends.
      error={
        record.error ??
        (record.isIdle && waiting?.problem?.code !== "plays.tieUndecided"
          ? waiting?.problem
          : undefined)
      }
      onSubmit={(body) => record.mutate({ body: { ...body, id } })}
      onUnreachable={keep}
    />
  )
}
