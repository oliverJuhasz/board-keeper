import { useId, type ComponentProps, type ReactNode } from "react"
import type { FieldError as FormFieldError } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { Field, FieldDescription, FieldError, FieldLabel } from "@/components/ui/field"
import { Textarea } from "@/components/ui/textarea"

type TextAreaFieldProps = Omit<ComponentProps<"textarea">, "id"> & {
  label: string
  hint?: ReactNode
  /** The field's error from React Hook Form. Its message is the name of a translated text. */
  error?: FormFieldError
}

/** Like TextField, for a text of several lines. The box grows with what is typed into it. */
export function TextAreaField({ label, hint, error, ...textarea }: TextAreaFieldProps) {
  const { t } = useTranslation()
  const id = useId()

  return (
    <Field data-invalid={error ? true : undefined}>
      <FieldLabel htmlFor={id}>{label}</FieldLabel>
      <Textarea
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={`${id}-hint ${id}-error`}
        {...textarea}
      />
      {hint && <FieldDescription id={`${id}-hint`}>{hint}</FieldDescription>}
      {error?.message && (
        <FieldError id={`${id}-error`} data-testid="field-error">
          {t(error.message as "validation.required")}
        </FieldError>
      )}
    </Field>
  )
}
