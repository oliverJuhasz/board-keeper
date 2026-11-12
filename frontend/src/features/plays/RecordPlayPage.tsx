import { zodResolver } from "@hookform/resolvers/zod"
import { useQueryClient } from "@tanstack/react-query"
import { XIcon } from "lucide-react"
import { useState } from "react"
import { Controller, useFieldArray, useForm, useWatch } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { Link, useNavigate, useSearchParams } from "react-router"
import { api, type Game, type Player } from "@/api/client"
import { FormProblem } from "@/components/FormProblem"
import { LinkButton } from "@/components/LinkButton"
import { PageHeader } from "@/components/PageHeader"
import { PageMessage } from "@/components/PageMessage"
import { QueryState } from "@/components/QueryState"
import { RadioField } from "@/components/RadioField"
import { TextAreaField } from "@/components/TextAreaField"
import { TextField } from "@/components/TextField"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import {
  FieldDescription,
  FieldError,
  FieldGroup,
  FieldLegend,
  FieldSet,
} from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select"
import { useRecordedAs } from "@/features/games/recordedAs"
import { RecordingChoices } from "@/features/games/RecordingChoices"
import { useGameModuleTexts } from "@/features/games/useGameModuleTexts"
import { useCurrentAccount } from "@/features/session/useSession"
import { todayIso } from "@/lib/dates"
import { playForm, tiedForFirst, toNewPlay } from "./playForm"

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
        return <RecordPlayForm game={game} players={players.data ?? []} />
      }}
    </QueryState>
  )
}

function RecordPlayForm({ game, players }: { game: Game; players: Player[] }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const account = useCurrentAccount()
  const recordedAs = useRecordedAs()
  const gameModuleTexts = useGameModuleTexts()
  const [changingRecording, setChangingRecording] = useState(false)

  const form = useForm({
    resolver: zodResolver(playForm),
    defaultValues: {
      playedOn: todayIso(),
      location: "",
      notes: "",
      // The Game's own choices, which the recorder can change for this Play.
      cooperative: game.cooperative,
      recordingMode: game.recordingMode,
      lowestScoreWins: game.lowestScoreWins,
      // Whoever records a Play has usually played in it.
      players: players.some((player) => player.id === account.playerId)
        ? [{ playerId: account.playerId, score: "", won: false }]
        : [],
      tieWinner: "",
      groupOutcome: "" as const,
      groupScore: "",
    },
  })
  const { errors, isSubmitted } = form.formState
  // useFieldArray manages a list inside the form: rows can be added and removed, and each row
  // gets an id of its own to use as its React key.
  const rows = useFieldArray({ control: form.control, name: "players" })
  // useWatch redraws this component whenever one of these fields changes, so the form can show
  // and hide what depends on them.
  const [cooperative, recordingMode, lowestScoreWins, enteredPlayers, tieWinner, groupOutcome] =
    useWatch({
      control: form.control,
      name: [
        "cooperative",
        "recordingMode",
        "lowestScoreWins",
        "players",
        "tieWinner",
        "groupOutcome",
      ],
    })

  const points = recordingMode === "POINTS"
  const nameOf = (playerId: string) => players.find((player) => player.id === playerId)?.name ?? ""
  const notYetAdded = players.filter(
    (player) => !enteredPlayers.some((entered) => entered.playerId === player.id),
  )
  const tied =
    !cooperative && points ? tiedForFirst({ players: enteredPlayers, lowestScoreWins }) : []
  // Once the form has been submitted, a change is checked at once, so a message goes away as
  // soon as its cause is fixed.
  const changed = { shouldValidate: isSubmitted, shouldDirty: true }

  const record = api.useMutation("post", "/api/plays", {
    onSuccess: (play) => {
      // The answer is the recorded Play. Keeping it lets the Play's page show it at once,
      // without a moment of "loading" first.
      const key = api.queryOptions("get", "/api/plays/{id}", {
        params: { path: { id: play.id } },
      }).queryKey
      queryClient.setQueryData(key, play)
      void navigate(`/plays/${play.id}`)
    },
  })
  const submit = form.handleSubmit((values) => record.mutate({ body: toNewPlay(game.id, values) }))

  const playersError = errors.players?.root?.message ?? errors.players?.message

  return (
    <form onSubmit={submit} noValidate className="space-y-6">
      <PageHeader
        title={t("plays.record.title")}
        description={t("plays.record.description", { game: game.name })}
      />
      <FieldGroup>
        <FormProblem error={record.error} reasons={gameModuleTexts} />

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField
            label={t("plays.record.date")}
            type="date"
            max={todayIso()}
            error={errors.playedOn}
            {...form.register("playedOn")}
          />
          <TextField
            label={t("plays.record.location")}
            hint={t("plays.record.locationHint")}
            autoComplete="off"
            error={errors.location}
            {...form.register("location")}
          />
        </div>

        <div className="space-y-4 rounded-lg border p-3">
          <div className="flex items-center justify-between gap-3">
            <p className="text-sm">
              <span className="text-muted-foreground">{t("plays.record.recordedAs")}: </span>
              <span className="font-medium">
                {recordedAs({ cooperative, recordingMode, lowestScoreWins })}
              </span>
            </p>
            {!changingRecording && (
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => setChangingRecording(true)}
              >
                {t("plays.record.change")}
              </Button>
            )}
          </div>
          {changingRecording && (
            <>
              <p className="text-sm text-muted-foreground">{t("plays.record.changeHint")}</p>
              <RecordingChoices
                value={{ cooperative, recordingMode, lowestScoreWins }}
                onChange={(recording) => {
                  form.setValue("cooperative", recording.cooperative, changed)
                  form.setValue("recordingMode", recording.recordingMode, changed)
                  form.setValue("lowestScoreWins", recording.lowestScoreWins, changed)
                }}
              />
            </>
          )}
        </div>

        <FieldSet>
          <FieldLegend variant="label">{t("plays.record.players")}</FieldLegend>
          {rows.fields.length === 0 ? (
            <p className="text-sm text-muted-foreground">{t("plays.record.noPlayers")}</p>
          ) : (
            <ul className="divide-y rounded-lg border">
              {rows.fields.map((row, index) => {
                const name = nameOf(row.playerId)
                const scoreError = errors.players?.[index]?.score
                return (
                  <li key={row.id} className="space-y-1 px-3 py-2">
                    <div className="flex items-center gap-3">
                      <span className="min-w-0 flex-1 truncate font-medium">{name}</span>
                      {!cooperative && points && (
                        <Input
                          type="number"
                          step={1}
                          className="w-24 text-right"
                          placeholder={t("plays.record.score")}
                          aria-label={t("plays.record.scoreOf", { name })}
                          aria-invalid={scoreError ? true : undefined}
                          {...form.register(`players.${index}.score`)}
                        />
                      )}
                      {!cooperative && !points && (
                        // The label makes the word "Won" clickable too.
                        <label className="flex items-center gap-2 text-sm">
                          {/* Controller connects a component that isn't a plain <input> to the
                              form: it hands over the field's value and a function to change it. */}
                          <Controller
                            control={form.control}
                            name={`players.${index}.won`}
                            render={({ field }) => (
                              <Checkbox
                                checked={field.value}
                                onCheckedChange={(checked) => {
                                  field.onChange(checked)
                                  if (isSubmitted) {
                                    void form.trigger("players")
                                  }
                                }}
                                aria-label={t("plays.record.wonBy", { name })}
                              />
                            )}
                          />
                          {t("plays.record.won")}
                        </label>
                      )}
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon-sm"
                        aria-label={t("plays.record.removePlayer", { name })}
                        onClick={() => rows.remove(index)}
                      >
                        <XIcon />
                      </Button>
                    </div>
                    {!cooperative && points && scoreError?.message && (
                      <FieldError className="text-right">
                        {t(scoreError.message as "validation.required")}
                      </FieldError>
                    )}
                  </li>
                )
              })}
            </ul>
          )}
          {notYetAdded.length > 0 ? (
            // Choosing a name adds that Player as a row; the list then goes back to its first line.
            <NativeSelect
              value=""
              aria-label={t("plays.record.addPlayer")}
              onChange={(event) =>
                rows.append({ playerId: event.target.value, score: "", won: false })
              }
            >
              <NativeSelectOption value="">{t("plays.record.choosePlayer")}</NativeSelectOption>
              {notYetAdded.map((player) => (
                <NativeSelectOption key={player.id} value={player.id}>
                  {player.name}
                </NativeSelectOption>
              ))}
            </NativeSelect>
          ) : (
            <FieldDescription>{t("plays.record.everyoneAdded")}</FieldDescription>
          )}
          {playersError && <FieldError>{t(playersError as "validation.required")}</FieldError>}
        </FieldSet>

        {tied.length > 0 && (
          <div className="space-y-2">
            <RadioField
              legend={t("plays.record.tie.legend")}
              value={tied.includes(tieWinner) ? tieWinner : ""}
              onChange={(playerId) => form.setValue("tieWinner", playerId, changed)}
              options={[
                { value: "", label: t("plays.record.tie.shared") },
                ...tied.map((playerId) => ({ value: playerId, label: nameOf(playerId) })),
              ]}
            />
            <FieldDescription>{t("plays.record.tie.hint")}</FieldDescription>
          </div>
        )}

        {cooperative && (
          <>
            <div className="space-y-2">
              <RadioField
                legend={t("plays.record.outcome")}
                value={groupOutcome}
                onChange={(outcome) => form.setValue("groupOutcome", outcome, changed)}
                options={[
                  { value: "won", label: t("plays.record.outcomeWon") },
                  { value: "lost", label: t("plays.record.outcomeLost") },
                ]}
              />
              {errors.groupOutcome?.message && (
                <FieldError>{t(errors.groupOutcome.message as "validation.required")}</FieldError>
              )}
            </div>
            {points && (
              <TextField
                label={t("plays.record.groupScore")}
                hint={t("plays.record.groupScoreHint")}
                type="number"
                step={1}
                error={errors.groupScore}
                {...form.register("groupScore")}
              />
            )}
          </>
        )}

        <TextAreaField
          label={t("plays.record.notes")}
          hint={t("plays.record.notesHint")}
          error={errors.notes}
          {...form.register("notes")}
        />

        <div className="flex gap-2">
          <Button type="submit" disabled={record.isPending}>
            {t("plays.record.submit")}
          </Button>
          <LinkButton to="/">{t("common.cancel")}</LinkButton>
        </div>
      </FieldGroup>
    </form>
  )
}
