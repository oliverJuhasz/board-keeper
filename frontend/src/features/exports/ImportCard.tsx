import { useMutation, useQueryClient } from "@tanstack/react-query"
import type { TFunction } from "i18next"
import { useId, useState } from "react"
import { useTranslation } from "react-i18next"
import { fetchClient, type Export, type ImportResult, type ImportReview } from "@/api/client"
import { problemCode, problemMessage } from "@/api/problems"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Field, FieldDescription, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { ImportReviewForm } from "./ImportReviewForm"
import { RefusedPlays } from "./RefusedPlays"

type Reviewed = { file: Export; review: ImportReview }

/**
 * Why a file couldn't be reviewed. A file that isn't JSON at all, or whose JSON can't be read as
 * an Export, is answered without a code, so it gets the same message as one that says it isn't
 * an Export. No answer at all is a problem with the connection.
 */
function reviewProblem(t: TFunction, error: unknown) {
  if (!(error instanceof TypeError) && problemCode(error) === undefined) {
    return t("problems.imports.notAnExport")
  }
  return problemMessage(t, error)
}

/**
 * Brings the Plays of an Export into this Installation (ADR 0021): choose the file, review what
 * it would do and who is who, then confirm. The browser keeps the file between the two steps;
 * the server keeps nothing.
 */
export function ImportCard() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const fileId = useId()
  const [reviewed, setReviewed] = useState<Reviewed>()
  const [result, setResult] = useState<ImportResult>()

  const review = useMutation({
    mutationFn: async (chosen: File): Promise<Reviewed> => {
      let file: Export
      try {
        file = JSON.parse(await chosen.text()) as Export
      } catch {
        throw { code: "imports.notAnExport" }
      }
      const { data, error } = await fetchClient.POST("/api/import/review", { body: file })
      if (error !== undefined) {
        throw error
      }
      return { file, review: data }
    },
    onSuccess: setReviewed,
  })

  const startAgain = () => {
    setReviewed(undefined)
    setResult(undefined)
    review.reset()
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("exportAndImport.import.title")}</CardTitle>
        <CardDescription>{t("exportAndImport.import.text")}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {result ? (
          <ImportDone result={result} onAgain={startAgain} />
        ) : reviewed ? (
          <ImportReviewForm
            file={reviewed.file}
            review={reviewed.review}
            onImported={(done) => {
              setResult(done)
              // Players, Games, Plays and statistics may all have changed.
              void queryClient.invalidateQueries()
            }}
            onCancel={startAgain}
          />
        ) : (
          <>
            <Field>
              <FieldLabel htmlFor={fileId}>{t("exportAndImport.import.file")}</FieldLabel>
              <Input
                id={fileId}
                type="file"
                accept=".json,application/json"
                disabled={review.isPending}
                aria-describedby={`${fileId}-hint`}
                onChange={(event) => {
                  const chosen = event.target.files?.[0]
                  // Emptied, so that choosing the same file again is noticed too.
                  event.target.value = ""
                  if (chosen) {
                    review.mutate(chosen)
                  }
                }}
              />
              <FieldDescription id={`${fileId}-hint`}>
                {t("exportAndImport.import.backupFirst")}
              </FieldDescription>
            </Field>
            {review.error && (
              <Alert variant="destructive">
                <AlertDescription>{reviewProblem(t, review.error)}</AlertDescription>
              </Alert>
            )}
          </>
        )}
      </CardContent>
    </Card>
  )
}

/** What an Import did. */
function ImportDone({ result, onAgain }: { result: ImportResult; onAgain: () => void }) {
  const { t } = useTranslation()

  return (
    <>
      <Alert>
        <AlertTitle>{t("exportAndImport.import.done", { count: result.added })}</AlertTitle>
        <AlertDescription>
          <ul className="list-disc space-y-1 pl-5">
            {result.alreadyHere > 0 && (
              <li>{t("exportAndImport.import.alreadyHere", { count: result.alreadyHere })}</li>
            )}
            {result.newPlayers > 0 && (
              <li>{t("exportAndImport.import.newPlayersAdded", { count: result.newPlayers })}</li>
            )}
            {result.newGames > 0 && (
              <li>{t("exportAndImport.import.newGamesAdded", { count: result.newGames })}</li>
            )}
          </ul>
        </AlertDescription>
      </Alert>
      {result.refused.length > 0 && <RefusedPlays refused={result.refused} />}
      <Button variant="outline" onClick={onAgain}>
        {t("exportAndImport.import.another")}
      </Button>
    </>
  )
}
