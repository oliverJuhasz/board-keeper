import { useTranslation } from "react-i18next"
import { FormProblem } from "@/components/FormProblem"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"

/** Asks "are you sure?" before something that is hard or impossible to undo. */
export function ConfirmDialog({
  title,
  description,
  confirm,
  onConfirm,
  pending,
  error,
  onClose,
}: {
  title: string
  description: string
  /** The text of the button that goes ahead, such as "Delete role". */
  confirm: string
  onConfirm: () => void
  pending: boolean
  error: unknown
  onClose: () => void
}) {
  const { t } = useTranslation()

  return (
    <AlertDialog open onOpenChange={(open) => !open && onClose()}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{title}</AlertDialogTitle>
          <AlertDialogDescription>{description}</AlertDialogDescription>
        </AlertDialogHeader>
        <FormProblem error={error} />
        <AlertDialogFooter>
          <AlertDialogCancel>{t("common.cancel")}</AlertDialogCancel>
          <AlertDialogAction
            variant="destructive"
            disabled={pending}
            onClick={onConfirm}
            data-testid="confirm"
          >
            {confirm}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
