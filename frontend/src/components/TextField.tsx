import { useId, type ComponentProps, type ReactNode } from "react"
import type { FieldError as FormFieldError } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { Field, FieldDescription, FieldError, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { PasswordInput } from "./PasswordInput"

type TextFieldProps = Omit<ComponentProps<"input">, "id"> & {
  label: string
  hint?: ReactNode
  /** The field's error from React Hook Form. Its message is the name of a translated text. */
  error?: FormFieldError
}

/**
 * One labelled text input of a form, with an optional hint and its error message.
 * Use it with React Hook Form: `<TextField label="…" error={errors.name} {...register("name")} />`.
 */
export function TextField({ label, hint, error, type, ...input }: TextFieldProps) {
  const { t } = useTranslation()
  const id = useId()
  const Control = type === "password" ? PasswordInput : Input

  return (
    <Field data-invalid={error ? true : undefined}>
      <FieldLabel htmlFor={id}>{label}</FieldLabel>
      <Control
        id={id}
        type={type}
        aria-invalid={error ? true : undefined}
        aria-describedby={`${id}-hint ${id}-error`}
        {...input}
      />
      {hint && <FieldDescription id={`${id}-hint`}>{hint}</FieldDescription>}
      {error?.message && (
        <FieldError id={`${id}-error`}>{t(error.message as "validation.required")}</FieldError>
      )}
    </Field>
  )
}
