import { zodResolver } from "@hookform/resolvers/zod"
import { XIcon } from "lucide-react"
import { useState } from "react"
import { Controller, useFieldArray, useForm, useWatch } from "react-hook-form"
import { useTranslation } from "react-i18next"
import type { NewPlay, Player } from "@/api/client"
import { FormProblem } from "@/components/FormProblem"
import { LinkButton } from "@/components/LinkButton"
import { PageHeader } from "@/components/PageHeader"
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
import { todayIso } from "@/lib/dates"
import { playForm, tiedForFirst, toNewPlay, type PlayFormValues } from "./playForm"

/**
 * The form for what is entered about a Play, used both to record a new one and to edit one.
 * It only collects and checks: the page around it says what the form starts with and where
 * the result is sent.
 */
export function PlayEntryForm({
  title,
  description,
  gameId,
  players,
  initial,
  submitLabel,
  cancelTo,
  pending,
  error,
  onSubmit,
}: {
  title: string
  description: string
  gameId: string
  /** Every Player there is, to pick from. */
  players: Player[]
  /** What the form starts with. Only looked at when the form is first drawn. */
  initial: PlayFormValues
  submitLabel: string
  /** Where "Cancel" leads. */
  cancelTo: string
  pending: boolean
  /** Why the server refused the last submission, if it did. */
  error: unknown
  onSubmit: (play: NewPlay) => void
}) {
  const { t } = useTranslation()
  const recordedAs = useRecordedAs()
  const gameModuleTexts = useGameModuleTexts()
  const [changingRecording, setChangingRecording] = useState(false)

  const form = useForm({ resolver: zodResolver(playForm), defaultValues: initial })
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

  const submit = form.handleSubmit((values) => onSubmit(toNewPlay(gameId, values)))

  const playersError = errors.players?.root?.message ?? errors.players?.message

  return (
    <form onSubmit={submit} noValidate className="space-y-6">
      <PageHeader title={title} description={description} />
      <FieldGroup>
        <FormProblem error={error} reasons={gameModuleTexts} />

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
          <Button type="submit" disabled={pending}>
            {submitLabel}
          </Button>
          <LinkButton to={cancelTo}>{t("common.cancel")}</LinkButton>
        </div>
      </FieldGroup>
    </form>
  )
}
