import { CheckIcon, CopyIcon } from "lucide-react"
import { useId, useState, type ReactNode } from "react"
import { useTranslation } from "react-i18next"
import { FormProblem } from "@/components/FormProblem"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"

type LinkDialogProps = {
  /** Shown first: what creating the link will do. */
  ask: { title: string; description: string; submit: string }
  /** Extra fields of the first step, such as a choice of Role. */
  children?: ReactNode
  onCreate: () => void
  creating: boolean
  error: unknown
  /** Shown once the link exists. */
  ready?: { title: string; description: string; label: string; link: string }
  onClose: () => void
}

/**
 * A dialog that creates a one-time link in two steps: first it asks, then it shows the link
 * with a button to copy it. Board Keeper sends no email, so whoever creates a link passes it on.
 */
export function LinkDialog({
  ask,
  children,
  onCreate,
  creating,
  error,
  ready,
  onClose,
}: LinkDialogProps) {
  const { t } = useTranslation()
  const linkFieldId = useId()
  const [copied, setCopied] = useState(false)

  async function copy(link: string) {
    await navigator.clipboard.writeText(link)
    setCopied(true)
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        {ready ? (
          <FieldGroup>
            <DialogHeader>
              <DialogTitle>{ready.title}</DialogTitle>
              <DialogDescription>{ready.description}</DialogDescription>
            </DialogHeader>
            <Field>
              <FieldLabel htmlFor={linkFieldId}>{ready.label}</FieldLabel>
              <Input
                id={linkFieldId}
                readOnly
                value={ready.link}
                onFocus={(event) => event.target.select()}
              />
            </Field>
            <DialogFooter>
              <Button variant="outline" onClick={() => void copy(ready.link)}>
                {copied ? <CheckIcon /> : <CopyIcon />}
                {copied ? t("link.copied") : t("link.copy")}
              </Button>
              <DialogClose render={<Button />}>{t("link.done")}</DialogClose>
            </DialogFooter>
          </FieldGroup>
        ) : (
          <FieldGroup>
            <DialogHeader>
              <DialogTitle>{ask.title}</DialogTitle>
              <DialogDescription>{ask.description}</DialogDescription>
            </DialogHeader>
            <FormProblem error={error} />
            {children}
            <DialogFooter>
              <DialogClose render={<Button variant="outline" />}>{t("common.cancel")}</DialogClose>
              <Button disabled={creating} onClick={onCreate}>
                {ask.submit}
              </Button>
            </DialogFooter>
          </FieldGroup>
        )}
      </DialogContent>
    </Dialog>
  )
}
