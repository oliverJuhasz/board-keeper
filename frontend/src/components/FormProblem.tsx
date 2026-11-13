import { useTranslation } from "react-i18next"
import type { Problem } from "@/api/client"
import { problemMessage } from "@/api/problems"
import { Alert, AlertDescription } from "@/components/ui/alert"
import i18n from "@/i18n"

/**
 * Shows why submitting a form failed, if it did. Pass the error of the form's mutation.
 *
 * Some answers carry a more exact reason than their code, whose text lives elsewhere: a Game
 * Module's reason for refusing a Play. Pass those texts as `reasons` to have them shown.
 */
export function FormProblem({
  error,
  reasons,
}: {
  error: unknown
  reasons?: Record<string, string>
}) {
  const { t } = useTranslation()
  if (!error) {
    return null
  }
  const reason = (error as Problem).reason
  // A reason is the name of a text: of the Game Module's, or one of the frontend's own for the
  // reasons that are the same for every Game.
  const explained =
    reason && (reasons?.[reason] ?? (i18n.exists(reason) ? t(reason as "problems.unknown") : ""))
  return (
    <Alert variant="destructive">
      <AlertDescription>{explained || problemMessage(t, error)}</AlertDescription>
    </Alert>
  )
}
