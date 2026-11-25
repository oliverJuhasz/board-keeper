import { zodResolver } from "@hookform/resolvers/zod"
import { Controller, useFieldArray, useForm, useWatch } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { z } from "zod"
import { api, type Export, type ImportResult, type ImportReview, type Player } from "@/api/client"
import { FormProblem } from "@/components/FormProblem"
import { QueryState } from "@/components/QueryState"
import { SelectField } from "@/components/SelectField"
import { TextField } from "@/components/TextField"
import { Button } from "@/components/ui/button"
import { FieldGroup } from "@/components/ui/field"
import { RefusedPlays } from "./RefusedPlays"

/** The value of a Player's drop-down that makes them a new Player, instead of one already here. */
const NEW = "new"

/** Names are unique whatever their capitals, as in the backend. */
const comparable = (name: string) => name.trim().toLocaleLowerCase()

/**
 * The form's rules. A new Player needs a name that no Player here has, and no other new one; each
 * message is the name of a text under "validation". That each Player here is chosen only once is
 * not a rule here: each drop-down only offers those that no other has chosen.
 */
function reviewFormFor(namesHere: string[]) {
  const taken = new Set(namesHere.map(comparable))
  return z.object({
    players: z
      .array(z.object({ fileId: z.string(), choice: z.string(), newName: z.string() }))
      .superRefine((rows, context) => {
        const newNames = new Set<string>()
        rows.forEach((row, index) => {
          if (row.choice !== NEW) {
            return
          }
          const name = row.newName.trim()
          const problem =
            name === ""
              ? "validation.required"
              : name.length > 50
                ? "validation.nameTooLong"
                : taken.has(comparable(name)) || newNames.has(comparable(name))
                  ? "validation.playerNameTaken"
                  : undefined
          newNames.add(comparable(name))
          if (problem) {
            context.addIssue({ code: "custom", message: problem, path: [index, "newName"] })
          }
        })
      }),
  })
}

/**
 * The review of an Import, before anything is written: what it would add, which Plays this
 * Installation can't take, and who each Player of the Export is here. Confirming imports it.
 */
export function ImportReviewForm(props: {
  file: Export
  review: ImportReview
  onImported: (result: ImportResult) => void
  onCancel: () => void
}) {
  const { t } = useTranslation()
  const players = api.useQuery("get", "/api/players")

  return (
    <QueryState
      queries={[players]}
      loading={t("exportAndImport.import.loading")}
      failed={t("exportAndImport.import.loadFailed")}
    >
      {() => <ReviewForm {...props} here={players.data ?? []} />}
    </QueryState>
  )
}

function ReviewForm({
  file,
  review,
  here,
  onImported,
  onCancel,
}: {
  file: Export
  review: ImportReview
  here: Player[]
  onImported: (result: ImportResult) => void
  onCancel: () => void
}) {
  const { t } = useTranslation()
  const form = useForm({
    resolver: zodResolver(reviewFormFor(here.map((player) => player.name))),
    // The Player here with the same name, if there is one; otherwise a new Player of that name.
    defaultValues: {
      players: review.players.map((player) => ({
        fileId: player.fileId,
        choice: player.suggestedPlayerId ?? NEW,
        newName: player.name,
      })),
    },
  })
  const rows = useFieldArray({ control: form.control, name: "players" })
  // The choices as they are now, to offer each drop-down only the Players nobody else has.
  const choices = useWatch({ control: form.control, name: "players" })

  const importFile = api.useMutation("post", "/api/import", { onSuccess: onImported })
  const submit = form.handleSubmit((values) =>
    importFile.mutate({
      body: {
        file,
        players: values.players.map((row) =>
          row.choice === NEW
            ? { fileId: row.fileId, newName: row.newName.trim() }
            : { fileId: row.fileId, playerId: row.choice },
        ),
      },
    }),
  )

  const toAdd = review.plays - review.alreadyHere - review.refused.length
  const newPlayers = choices.filter((row) => row.choice === NEW).length
  const newGames = review.games.filter((game) => game.match === "NEW")

  return (
    <form onSubmit={(event) => void submit(event)} noValidate className="space-y-6">
      <ul className="list-disc space-y-1 pl-5" data-testid="import-summary">
        <li>{t("exportAndImport.import.toAdd", { count: toAdd })}</li>
        {review.alreadyHere > 0 && (
          <li>{t("exportAndImport.import.alreadyHere", { count: review.alreadyHere })}</li>
        )}
        {newPlayers > 0 && <li>{t("exportAndImport.import.newPlayers", { count: newPlayers })}</li>}
        {newGames.length > 0 && (
          <li>
            {t("exportAndImport.import.newGames", {
              count: newGames.length,
              names: newGames.map((game) => game.name).join(", "),
            })}
          </li>
        )}
      </ul>

      {review.refused.length > 0 && <RefusedPlays refused={review.refused} />}

      <section className="space-y-3" data-testid="who-is-who">
        <div className="space-y-1">
          <h2 className="font-heading font-medium">{t("exportAndImport.import.whoIsWho")}</h2>
          <p className="text-muted-foreground">{t("exportAndImport.import.whoIsWhoHint")}</p>
        </div>
        <FieldGroup>
          {rows.fields.map((row, index) => {
            const player = review.players[index]
            const takenElsewhere = new Set(
              choices.filter((_, other) => other !== index).map((other) => other.choice),
            )
            return (
              <div key={row.id} className="space-y-3" data-testid="import-player">
                <Controller
                  control={form.control}
                  name={`players.${index}.choice`}
                  render={({ field }) => (
                    <SelectField
                      label={player.name}
                      testId="import-player-choice"
                      value={field.value}
                      onChange={field.onChange}
                      options={[
                        { value: NEW, label: t("exportAndImport.import.newPlayer") },
                        ...here
                          .filter((candidate) => !takenElsewhere.has(candidate.id))
                          .map((candidate) => ({ value: candidate.id, label: candidate.name })),
                      ]}
                    />
                  )}
                />
                {choices[index]?.choice === NEW && (
                  <TextField
                    label={t("exportAndImport.import.newPlayerName", { name: player.name })}
                    data-testid="import-player-name"
                    error={form.formState.errors.players?.[index]?.newName}
                    {...form.register(`players.${index}.newName`)}
                  />
                )}
              </div>
            )
          })}
        </FieldGroup>
      </section>

      <p className="text-muted-foreground">{t("exportAndImport.import.backupFirst")}</p>
      <FormProblem error={importFile.error} />
      <div className="flex flex-wrap gap-2">
        <Button type="submit" disabled={importFile.isPending} data-testid="confirm-import">
          {t("exportAndImport.import.confirm")}
        </Button>
        <Button type="button" variant="outline" onClick={onCancel} data-testid="cancel-import">
          {t("common.cancel")}
        </Button>
      </div>
    </form>
  )
}
