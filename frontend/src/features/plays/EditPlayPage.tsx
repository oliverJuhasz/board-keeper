import { useQueryClient } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { Link, useNavigate, useParams } from "react-router"
import { api, type GameModule, type Play, type Player } from "@/api/client"
import { problemCode } from "@/api/problems"
import { PageMessage } from "@/components/PageMessage"
import { QueryState } from "@/components/QueryState"
import { moduleWithId, useGameModules, useGameName } from "@/features/games/useGameModules"
import { useGameModuleTextsQuery } from "@/features/games/useGameModuleTexts"
import { PlayEntryForm } from "./PlayEntryForm"
import { fromPlay } from "./playForm"
import { playKey, playsKey } from "./playQueries"

/** Edits the Play named in the address: /plays/<id>/edit. */
export function EditPlayPage() {
  const { t } = useTranslation()
  const { id = "" } = useParams()
  const play = api.useQuery("get", "/api/plays/{id}", { params: { path: { id } } })
  const players = api.useQuery("get", "/api/players")
  const modules = useGameModules()
  const texts = useGameModuleTextsQuery()

  if (problemCode(play.error) === "plays.notFound") {
    return (
      <PageMessage title={t("problems.plays.notFound")}>
        <Link to="/plays" className="text-foreground underline underline-offset-4">
          {t("plays.page.backToPlays")}
        </Link>
      </PageMessage>
    )
  }
  return (
    <QueryState
      queries={[play, players, modules, texts]}
      loading={t("plays.page.loading")}
      failed={t("plays.page.loadFailed")}
    >
      {() => {
        if (!play.data) {
          return null
        }
        if (!play.data.canChange) {
          return (
            <PageMessage title={t("plays.edit.notAllowed.title")}>
              <p>{t("plays.edit.notAllowed.text")}</p>
              <Link
                to={`/plays/${play.data.id}`}
                className="text-foreground underline underline-offset-4"
                data-testid="back-to-play"
              >
                {t("plays.edit.notAllowed.link")}
              </Link>
            </PageMessage>
          )
        }
        // The Play is edited on the pad of the module that scored it, which need not be the
        // module that scores its Game today.
        const module = moduleWithId(modules.data, play.data.gameModuleId)
        if (!module) {
          return <PageMessage title={t("problems.plays.gameModuleMissing")} />
        }
        return <EditPlay play={play.data} module={module} players={players.data ?? []} />
      }}
    </QueryState>
  )
}

function EditPlay({
  play,
  module,
  players,
}: {
  play: Play
  module: GameModule
  players: Player[]
}) {
  const { t } = useTranslation()
  const gameName = useGameName()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const change = api.useMutation("put", "/api/plays/{id}", {
    onSuccess: (changed) => {
      queryClient.setQueryData(playKey(changed.id), changed)
      queryClient.removeQueries({ queryKey: playsKey })
      void navigate(`/plays/${changed.id}`)
    },
  })

  return (
    <PlayEntryForm
      title={t("plays.edit.title")}
      description={t("plays.edit.description", { game: gameName(play.game) })}
      gameId={play.game.id}
      module={module}
      players={players}
      initial={fromPlay(module, play)}
      submitLabel={t("plays.edit.submit")}
      cancelTo={`/plays/${play.id}`}
      pending={change.isPending}
      error={change.error}
      onSubmit={(body) => change.mutate({ params: { path: { id: play.id } }, body })}
    />
  )
}
