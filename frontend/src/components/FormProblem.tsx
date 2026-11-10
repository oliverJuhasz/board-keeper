import { useTranslation } from "react-i18next"
import { problemMessage } from "@/api/problems"
import { Alert, AlertDescription } from "@/components/ui/alert"

/** Shows why submitting a form failed, if it did. Pass the error of the form's mutation. */
export function FormProblem({ error }: { error: unknown }) {
  const { t } = useTranslation()
  if (!error) {
    return null
  }
  return (
    <Alert variant="destructive">
      <AlertDescription>{problemMessage(t, error)}</AlertDescription>
    </Alert>
  )
}
