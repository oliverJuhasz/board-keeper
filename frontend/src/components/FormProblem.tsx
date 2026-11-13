import { useTranslation } from "react-i18next"
import { explainProblem } from "@/api/problems"
import { Alert, AlertDescription } from "@/components/ui/alert"

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
  return (
    <Alert variant="destructive">
      <AlertDescription>{explainProblem(t, error, reasons)}</AlertDescription>
    </Alert>
  )
}
