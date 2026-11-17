import { zodResolver } from "@hookform/resolvers/zod"
import { onlineManager, useQueryClient } from "@tanstack/react-query"
import { XIcon } from "lucide-react"
import { useRef, useState } from "react"
import { Controller, useFieldArray, useForm, useWatch } from "react-hook-form"
import { useTranslation } from "react-i18next"
import type { GameModule, NewPlay, Player, ScoreCategory } from "@/api/client"
import { CheckboxField } from "@/components/CheckboxField"
import { FormProblem } from "@/components/FormProblem"
import { LinkButton } from "@/components/LinkButton"
import { PageHeader } from "@/components/PageHeader"
import { RadioField } from "@/components/RadioField"
import { SelectField } from "@/components/SelectField"
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
import { GENERIC } from "@/features/games/useGameModules"
import { useGameModuleTexts } from "@/features/games/useGameModuleTexts"
import { PlayerNameDialog } from "@/features/players/PlayerNameDialog"
import { useCan } from "@/features/session/useSession"
import { todayIso } from "@/lib/dates"
import {
  categoriesOnPad,
  entersScores,
  fewestPlayers,
  isCooperative,
  newPlayerRow,
  playFormFor,
  SHARED,
  soloOf,
  toNewPlay,
  toPreviewRequest,
  groupRowsOf,
  recordingModeOf,
  variantsFor,
  victoryTypeOf,
  victoryTypesFor,
  wonOutright,
  type PlayFormValues,
} from "./playForm"
import { canBePreviewed, previewOptions, usePlayPreview } from "./usePlayPreview"
import { useOnline } from "@/lib/online"
import { Alert, AlertDescription } from "@/components/ui/alert"

/** What the list of Players to add holds for "someone new", where the others have their id. */
const NEW_PLAYER = "new"

/** A Variant with more options than this is a drop-down rather than a list of radio buttons. */
const RADIO_BUTTONS_AT_MOST = 5

/**
 * The form for what is entered about a Play, used both to record a new one and to edit one.
 * It only collects and checks: the page around it says what the form starts with and where
 * the result is sent.
 *
 * The form is drawn from the score pad of the Game Module that scores the Play: its Variants
 * become choices, and its Score Categories the rows of a score pad. What each is called comes
 * from the module's own texts.
 */
export function PlayEntryForm({
  title,
  description,
  gameId,
  module,
  players,
  initial,
  submitLabel,
  cancelTo,
  pending,
  error,
  onSubmit,
  onUnreachable,
}: {
  title: string
  description: string
  gameId: string
  /** The Game Module that scores the Play. */
  module: GameModule
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
  /**
   * Called instead of onSubmit when Board Keeper can't be reached, for a page that keeps the
   * Play on the device to send later. The form then neither shows totals nor asks about a tie:
   * the server works those out once the Play is sent. Without it, the form says it can't reach
   * the server, as for any other failure.
   */
  onUnreachable?: (play: NewPlay) => void
}) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const can = useCan()
  const recordedAs = useRecordedAs()
  const texts = useGameModuleTexts()
  const [changingRecording, setChangingRecording] = useState(false)
  const [previewProblem, setPreviewProblem] = useState<unknown>()
  const online = useOnline()
  const pad = module.scorePad
  const generic = module.key === GENERIC
  /** A text of the module, such as its name for a row of the pad. */
  const moduleText = (name: string) => texts[`${module.key}.${name}`]

  const form = useForm({ resolver: zodResolver(playFormFor(module)), defaultValues: initial })
  const { errors, isSubmitted } = form.formState
  // useFieldArray manages a list inside the form: rows can be added and removed, and each row
  // gets an id of its own to use as its React key.
  const rows = useFieldArray({ control: form.control, name: "players" })
  // useWatch redraws this component whenever a field changes, so the form can show and hide
  // what depends on the others. Every field has a starting value, so the values are complete.
  const play = useWatch({ control: form.control }) as PlayFormValues

  const cooperative = isCooperative(module, play)
  // How the Play could be won, and was. Won outright, as by a supremacy, nobody counted points:
  // the recorder ticks the winner, as when only the winner is recorded.
  const victoryTypes = victoryTypesFor(module, play)
  const victoryType = victoryTypeOf(module, play)
  const outright = wonOutright(module, play)
  const points = recordingModeOf(module, play) === "POINTS"
  // A Play of one, of a Game that can be played alone: for score only, or against an opponent.
  const solo = soloOf(module, play)
  const categories = entersScores(module, play)
    ? categoriesOnPad(pad, play.setup, solo !== undefined)
    : []
  // One row fits next to each Player's name. More rows are drawn as a pad, like the paper one.
  const drawnAsPad = categories.length > 1
  const recording = {
    cooperative,
    recordingMode: play.recordingMode,
    lowestScoreWins: play.setup.lowestScoreWins === "true",
  }

  // Players that were added from this form. They are kept here as well, so their names show
  // at once, without waiting for the list of Players to be fetched again.
  const [addedHere, setAddedHere] = useState<Player[]>([])
  const [addingPlayer, setAddingPlayer] = useState(false)
  // The list of Players to add. useRef holds on to its element, to give it focus again after
  // the dialog for someone new has closed.
  const addPlayerList = useRef<HTMLSelectElement>(null)
  const allPlayers = [
    ...players,
    ...addedHere.filter((added) => !players.some((player) => player.id === added.id)),
  ]
  const nameOf = (playerId: string) =>
    allPlayers.find((player) => player.id === playerId)?.name ?? ""
  const notYetAdded = allPlayers.filter(
    (player) => !play.players.some((entered) => entered.playerId === player.id),
  )
  const full = pad.maxPlayers !== undefined && play.players.length >= pad.maxPlayers

  /**
   * Adds a Player to the Play. Focus stays on the list of Players to add, ready for the next one.
   * Once that list goes away, because the Game has no seat left or nobody is left to add, focus
   * moves on to the new Player's first entry instead.
   */
  function addPlayer(playerId: string) {
    const seatLeft = pad.maxPlayers === undefined || play.players.length + 1 < pad.maxPlayers
    const someoneLeft =
      can("CREATE_PLAYERS") || notYetAdded.some((player) => player.id !== playerId)
    // By default, append moves focus to the first field of the row it adds.
    rows.append(newPlayerRow(pad, playerId), { shouldFocus: !(seatLeft && someoneLeft) })
  }

  // The server works out the totals, the Placings, and whether first place is a tie that the
  // scores can't decide. Only then is the recorder asked who won.
  const preview = usePlayPreview(module, play)
  const tie = preview?.tieForRecorder ?? []
  const tieAnswer = play.tieWinner === SHARED || tie.includes(play.tieWinner) ? play.tieWinner : ""

  function placingOf(playerId: string) {
    const placing = preview?.results.find((result) => result.playerId === playerId)?.placing
    // The preview doesn't know the recorder's answer yet: show it as it will be recorded.
    if (tie.includes(playerId) && tie.includes(tieAnswer)) {
      return playerId === tieAnswer ? 1 : 2
    }
    return placing
  }

  // Once the form has been submitted, a change is checked at once, so a message goes away as
  // soon as its cause is fixed.
  const changed = { shouldValidate: isSubmitted, shouldDirty: true }

  const submit = form.handleSubmit(async (values) => {
    // Without a connection there is nobody to ask how the Play ends: keep it as it is.
    if (onUnreachable && !onlineManager.isOnline()) {
      onUnreachable(toNewPlay(module, gameId, values))
      return
    }
    let tieToDecide: string[] = []
    if (canBePreviewed(module, values)) {
      try {
        // Ask about exactly what is being submitted. If the form already asked while the
        // recorder was typing, the answer is still at hand and nothing is sent. Once is enough:
        // when the server can't be reached, the Play is kept for later straight away.
        const outcome = await queryClient.fetchQuery({
          ...previewOptions(toPreviewRequest(module, values)),
          retry: false,
        })
        tieToDecide = outcome.tieForRecorder
      } catch (refusal) {
        // fetch() fails with a TypeError when there is no answer at all.
        if (onUnreachable && refusal instanceof TypeError) {
          onUnreachable(toNewPlay(module, gameId, values))
          return
        }
        setPreviewProblem(refusal)
        return
      }
      const answered = values.tieWinner === SHARED || tieToDecide.includes(values.tieWinner)
      if (tieToDecide.length > 0 && !answered) {
        form.setError("tieWinner", { message: "validation.tieUnanswered" })
        return
      }
    }
    setPreviewProblem(undefined)
    onSubmit(toNewPlay(module, gameId, values, tieToDecide))
  })

  /** The message for a value in a row of the pad, with the row's own limits filled in. */
  function scoreMessage(category: ScoreCategory, message: string | undefined) {
    return message
      ? t(message as "validation.scoreStep", {
          min: category.min,
          max: category.max,
          step: category.step,
        })
      : undefined
  }

  const playersError = errors.players?.root?.message ?? errors.players?.message
  // The messages for the pad, one line each: "Buttons of Anna: Enter a multiple of 3."
  const padProblems = drawnAsPad
    ? rows.fields.flatMap((row, index) =>
        categories.flatMap((category) => {
          const message = scoreMessage(
            category,
            errors.players?.[index]?.scores?.[category.key]?.message,
          )
          return message
            ? [
                `${t("plays.record.valueOf", {
                  category: moduleText(`category.${category.key}`),
                  name: nameOf(row.playerId),
                })}: ${message}`,
              ]
            : []
        }),
      )
    : []

  return (
    <form onSubmit={submit} noValidate className="space-y-6">
      <PageHeader title={title} description={description} />
      <FieldGroup>
        {onUnreachable && !online && (
          <Alert>
            <AlertDescription>{t("plays.record.offline")}</AlertDescription>
          </Alert>
        )}
        <FormProblem error={error ?? previewProblem} reasons={texts} />

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

        {/* Who played comes first: whether the Play is a Solo Play decides which Variants and
            ways to win there are, and what the recording is called. */}
        <FieldSet>
          <FieldLegend variant="label">{t("plays.record.players")}</FieldLegend>
          {rows.fields.length === 0 ? (
            <p className="text-sm text-muted-foreground">{t("plays.record.noPlayers")}</p>
          ) : (
            <ul className="divide-y rounded-lg border">
              {rows.fields.map((row, index) => {
                const name = nameOf(row.playerId)
                const only = drawnAsPad ? undefined : categories[0]
                const scoreError = only
                  ? scoreMessage(only, errors.players?.[index]?.scores?.[only.key]?.message)
                  : undefined
                return (
                  <li key={row.id} className="space-y-1 px-3 py-2">
                    <div className="flex items-center gap-3">
                      <span className="min-w-0 flex-1 truncate font-medium">{name}</span>
                      {only && (
                        <Input
                          type="number"
                          step={only.step}
                          className="w-24 text-right"
                          placeholder={moduleText(`category.${only.key}`)}
                          aria-label={t("plays.record.valueOf", {
                            category: moduleText(`category.${only.key}`),
                            name,
                          })}
                          aria-invalid={scoreError ? true : undefined}
                          {...form.register(`players.${index}.scores.${only.key}`)}
                        />
                      )}
                      {!cooperative && !points && solo?.kind !== "FOR_SCORE_ONLY" && (
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
                    {/* The Player Setup, such as their Wonder: a drop-down for each choice. Each can
                        be left as it is, for a choice nobody remembers. */}
                    {pad.playerSetup.length > 0 && (
                      <div className="flex flex-wrap gap-2">
                        {pad.playerSetup.map((choice) => {
                          // Where each option is there once, such as a Wonder board, the ones
                          // another Player has already can't be picked.
                          const taken = choice.oncePerPlay
                            ? play.players
                                .filter((_, other) => other !== index)
                                .map((other) => other.setup[choice.key])
                            : []
                          return (
                            <NativeSelect
                              key={choice.key}
                              size="sm"
                              aria-label={t("plays.record.valueOf", {
                                category: moduleText(`playerSetup.${choice.key}`),
                                name,
                              })}
                              {...form.register(`players.${index}.setup.${choice.key}`)}
                            >
                              <NativeSelectOption value="">
                                {moduleText(`playerSetup.${choice.key}`)}
                              </NativeSelectOption>
                              {choice.options.map((option) => (
                                <NativeSelectOption
                                  key={option}
                                  value={option}
                                  disabled={taken.includes(option)}
                                >
                                  {moduleText(`playerSetup.${choice.key}.${option}`)}
                                </NativeSelectOption>
                              ))}
                            </NativeSelect>
                          )
                        })}
                      </div>
                    )}
                    {scoreError && <FieldError className="text-right">{scoreError}</FieldError>}
                    {pad.playerSetup.map((choice) => {
                      const message = errors.players?.[index]?.setup?.[choice.key]?.message
                      return (
                        message && (
                          <FieldError key={choice.key}>
                            {t(message as "validation.chosenTwice", {
                              choice: moduleText(`playerSetup.${choice.key}`),
                            })}
                          </FieldError>
                        )
                      )
                    })}
                  </li>
                )
              })}
            </ul>
          )}
          {full ? (
            <FieldDescription>
              {t("plays.record.playersFull", { max: pad.maxPlayers })}
            </FieldDescription>
          ) : notYetAdded.length > 0 || can("CREATE_PLAYERS") ? (
            // Choosing a name adds that Player as a row; the list then goes back to its first line.
            <NativeSelect
              ref={addPlayerList}
              value=""
              aria-label={t("plays.record.addPlayer")}
              onChange={(event) => {
                if (event.target.value === NEW_PLAYER) {
                  setAddingPlayer(true)
                } else {
                  addPlayer(event.target.value)
                }
              }}
            >
              <NativeSelectOption value="">{t("plays.record.choosePlayer")}</NativeSelectOption>
              {notYetAdded.map((player) => (
                <NativeSelectOption key={player.id} value={player.id}>
                  {player.name}
                </NativeSelectOption>
              ))}
              {/* For someone who has never played before: add them without leaving the form. */}
              {can("CREATE_PLAYERS") && (
                <NativeSelectOption value={NEW_PLAYER}>
                  {t("plays.record.newPlayer")}
                </NativeSelectOption>
              )}
            </NativeSelect>
          ) : (
            <FieldDescription>{t("plays.record.everyoneAdded")}</FieldDescription>
          )}
          {playersError && (
            <FieldError>
              {t(playersError as "validation.playersTooFew", {
                min: fewestPlayers(module, play),
                max: pad.maxPlayers,
              })}
            </FieldError>
          )}
        </FieldSet>

        <div className="space-y-4 rounded-lg border p-3">
          <div className="flex items-center justify-between gap-3">
            <p className="text-sm">
              <span className="text-muted-foreground">{t("plays.record.recordedAs")}: </span>
              <span className="font-medium">
                {generic
                  ? recordedAs(recording)
                  : points
                    ? t("recording.points")
                    : solo?.kind === "AGAINST_OPPONENT" || cooperative
                      ? t("recording.wonOrLostOnly")
                      : t("recording.winnerOnly")}
              </span>
            </p>
            {/* A Play won outright has nothing to choose: only its winner is recorded. */}
            {!changingRecording && !outright && (
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
          {changingRecording && !outright && (
            <>
              <p className="text-sm text-muted-foreground">{t("plays.record.changeHint")}</p>
              {generic ? (
                // A Game without a module of its own: the recorder says what kind of Game it is.
                <RecordingChoices
                  value={recording}
                  onChange={(choice) => {
                    form.setValue("setup.cooperative", String(choice.cooperative), changed)
                    form.setValue("recordingMode", choice.recordingMode, changed)
                    form.setValue("setup.lowestScoreWins", String(choice.lowestScoreWins), changed)
                  }}
                />
              ) : (
                <RadioField
                  legend={t("recording.recording")}
                  value={play.recordingMode}
                  onChange={(recordingMode) =>
                    form.setValue("recordingMode", recordingMode, changed)
                  }
                  options={[
                    {
                      value: "POINTS",
                      label: t("recording.points"),
                      hint: cooperative
                        ? t("plays.record.groupPadHint")
                        : t("plays.record.padHint"),
                    },
                    {
                      value: "WINNER_ONLY",
                      // Against an opponent, a Solo Play is won or lost, and so is a cooperative one.
                      label:
                        solo?.kind === "AGAINST_OPPONENT" || cooperative
                          ? t("recording.wonOrLostOnly")
                          : t("recording.winnerOnly"),
                      hint: t("recording.outcomeHint"),
                    },
                  ]}
                />
              )}
            </>
          )}
          {errors.recordingMode?.message && (
            <FieldError>{t(errors.recordingMode.message as "validation.required")}</FieldError>
          )}
        </div>

        {/* The Expansions the Play was played with. Each can add rows to the pad, or ways to win. */}
        {pad.expansions.length > 0 && (
          <FieldSet>
            <FieldLegend variant="label">{t("plays.record.expansions")}</FieldLegend>
            {pad.expansions.map((expansion) => (
              <CheckboxField
                key={expansion.key}
                label={moduleText(`expansion.${expansion.key}`)}
                checked={play.setup[expansion.key] === "true"}
                onChange={(on) => form.setValue(`setup.${expansion.key}`, String(on), changed)}
              />
            ))}
          </FieldSet>
        )}

        {/* The Variants of a Game with a module of its own. The Generic Module's two are the
            recording choices above. */}
        {!generic &&
          variantsFor(module, play).map((variant) =>
            variant.kind === "ON_OFF" ? (
              <CheckboxField
                key={variant.key}
                label={moduleText(`variant.${variant.key}`)}
                hint={moduleText(`variant.${variant.key}.hint`)}
                checked={play.setup[variant.key] === "true"}
                onChange={(on) => form.setValue(`setup.${variant.key}`, String(on), changed)}
              />
            ) : (
              <ChoiceField
                key={variant.key}
                label={moduleText(`variant.${variant.key}`)}
                value={play.setup[variant.key]}
                onChange={(option) => form.setValue(`setup.${variant.key}`, option, changed)}
                options={choicesOf(variant).map((option) => ({
                  value: option,
                  // A number may have a name of its own, such as "Base level" for 0.
                  label:
                    moduleText(`variant.${variant.key}.${option}`) ??
                    (variant.kind === "RANGE" ? option : ""),
                }))}
              />
            ),
          )}

        {victoryTypes.length > 0 && (
          <div className="space-y-2">
            <RadioField
              legend={t("plays.record.victoryType")}
              value={victoryType?.key ?? ""}
              onChange={(key) => form.setValue("victoryType", key, changed)}
              options={victoryTypes.map((option) => ({
                value: option.key,
                label: moduleText(`victoryType.${option.key}`),
              }))}
            />
            {outright && <FieldDescription>{t("plays.record.outrightHint")}</FieldDescription>}
          </div>
        )}

        {drawnAsPad && rows.fields.length > 0 && (
          <div className="space-y-2">
            {/* Like the paper pad: a row for each Score Category and a column for each Player.
                It scrolls sideways if the Players don't fit. */}
            <div className="overflow-x-auto rounded-lg border">
              <table className="w-full text-sm" aria-label={t("plays.record.pad")}>
                <thead>
                  <tr>
                    <td />
                    {rows.fields.map((row) => (
                      <th
                        key={row.id}
                        scope="col"
                        className="max-w-24 truncate px-2 py-2 text-right font-medium"
                      >
                        {nameOf(row.playerId)}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {categories.map((category) => (
                    <tr key={category.key} className="border-t">
                      {/* A row that doesn't count toward the total, such as the opponent's
                          score in a Solo Play, is set apart a little. */}
                      <th
                        scope="row"
                        className={
                          category.countsTowardTotal
                            ? "px-3 py-1.5 text-left font-normal"
                            : "px-3 py-1.5 text-left font-normal text-muted-foreground italic"
                        }
                      >
                        {moduleText(`category.${category.key}`)}
                      </th>
                      {rows.fields.map((row, index) => (
                        <td key={row.id} className="px-2 py-1.5 text-right">
                          <Input
                            type="number"
                            step={category.step}
                            className="inline-block w-20 text-right"
                            aria-label={t("plays.record.valueOf", {
                              category: moduleText(`category.${category.key}`),
                              name: nameOf(row.playerId),
                            })}
                            aria-invalid={
                              errors.players?.[index]?.scores?.[category.key] ? true : undefined
                            }
                            {...form.register(`players.${index}.scores.${category.key}`)}
                          />
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
                {/* Worked out by the server once every row is filled in. */}
                <tfoot>
                  <tr className="border-t font-medium">
                    <th scope="row" className="px-3 py-2 text-left">
                      {t("plays.record.total")}
                    </th>
                    {rows.fields.map((row) => (
                      <td key={row.id} className="px-2 py-2 pr-5 text-right tabular-nums">
                        {preview?.results.find((result) => result.playerId === row.playerId)
                          ?.total ?? "–"}
                      </td>
                    ))}
                  </tr>
                  {/* A Solo Play has no Placing. Against an opponent it is won or lost; for
                      score only there is nothing more to show. */}
                  {!solo && (
                    <tr className="border-t text-muted-foreground">
                      <th scope="row" className="px-3 py-2 text-left font-normal">
                        {t("plays.record.placing")}
                      </th>
                      {rows.fields.map((row) => (
                        <td key={row.id} className="px-2 py-2 pr-5 text-right tabular-nums">
                          {placingOf(row.playerId) ?? "–"}
                        </td>
                      ))}
                    </tr>
                  )}
                  {solo?.kind === "AGAINST_OPPONENT" && (
                    <tr className="border-t text-muted-foreground">
                      <th scope="row" className="px-3 py-2 text-left font-normal">
                        {t("plays.record.soloResult")}
                      </th>
                      {rows.fields.map((row) => {
                        const won = preview?.results.find(
                          (result) => result.playerId === row.playerId,
                        )?.won
                        return (
                          <td key={row.id} className="px-2 py-2 pr-5 text-right">
                            {won === undefined
                              ? "–"
                              : won
                                ? t("plays.record.won")
                                : t("plays.record.lost")}
                          </td>
                        )
                      })}
                    </tr>
                  )}
                </tfoot>
              </table>
            </div>
            {padProblems.length > 0 && <FieldError errors={padProblems.map(asError)} />}
          </div>
        )}

        {tie.length > 0 && (
          <div className="space-y-2">
            <RadioField
              legend={t("plays.record.tie.legend")}
              value={tieAnswer}
              onChange={(answer) => {
                form.setValue("tieWinner", answer, { shouldDirty: true })
                form.clearErrors("tieWinner")
              }}
              options={[
                ...tie.map((playerId) => ({ value: playerId, label: nameOf(playerId) })),
                { value: SHARED, label: t("plays.record.tie.shared") },
              ]}
            />
            {/* How this Game decides a tie, in the module's own words. */}
            <FieldDescription>{moduleText("tie")}</FieldDescription>
            {errors.tieWinner?.message && (
              <FieldError>{t(errors.tieWinner.message as "validation.required")}</FieldError>
            )}
          </div>
        )}

        {cooperative && (
          <>
            <div className="space-y-2">
              <RadioField
                legend={t("plays.record.outcome")}
                value={play.groupOutcome}
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
            {points && !pad.cooperative && (
              <TextField
                label={t("plays.record.groupScore")}
                hint={t("plays.record.groupScoreHint")}
                type="number"
                step={1}
                error={errors.groupScore}
                {...form.register("groupScore")}
              />
            )}
            {/* A cooperative Game with a module of its own: the group's rows, from which the
                module works out the score. A row may be counted differently after a win and
                after a loss, and then says so. */}
            {groupRowsOf(module, play).map((category) => (
              <TextField
                key={category.key}
                label={
                  moduleText(`category.${category.key}.${play.groupOutcome}`) ??
                  moduleText(`category.${category.key}`)
                }
                type="number"
                step={category.step}
                error={
                  errors.groupScores?.[category.key]?.message
                    ? {
                        type: "validate",
                        message: errors.groupScores[category.key]?.message,
                      }
                    : undefined
                }
                {...form.register(`groupScores.${category.key}`)}
              />
            ))}
            {groupRowsOf(module, play).length > 0 && (
              <p className="font-medium">
                {t("plays.page.groupScore", { score: preview?.group?.total ?? "–" })}
              </p>
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

      {addingPlayer && (
        <PlayerNameDialog
          onAdded={(player) => {
            setAddedHere((already) => [...already, player])
            addPlayer(player.id)
          }}
          onClose={() => setAddingPlayer(false)}
          // Back to the list, for the next one. Once it has gone, because nobody more can be
          // added, addPlayer has sent focus to the new Player's first entry: leave it there.
          finalFocus={() => addPlayerList.current ?? false}
        />
      )}
    </form>
  )
}

/** A line of text in the form FieldError takes a list of. */
function asError(message: string) {
  return { message }
}

/** The options of a Variant that is a choice, or the numbers of one that is a range. */
function choicesOf(variant: GameModule["scorePad"]["variants"][number]) {
  if (variant.kind === "RANGE" && variant.min !== undefined && variant.max !== undefined) {
    return Array.from({ length: variant.max - variant.min + 1 }, (_, index) =>
      String((variant.min ?? 0) + index),
    )
  }
  return variant.options ?? []
}

/** A choice of a few options as radio buttons, and of many as a drop-down. */
function ChoiceField({
  label,
  value,
  onChange,
  options,
}: {
  label: string
  value: string
  onChange: (value: string) => void
  options: { value: string; label: string }[]
}) {
  return options.length > RADIO_BUTTONS_AT_MOST ? (
    <SelectField label={label} value={value} onChange={onChange} options={options} />
  ) : (
    <RadioField legend={label} value={value} onChange={onChange} options={options} />
  )
}
