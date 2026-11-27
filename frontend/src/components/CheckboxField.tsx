import { useId, type ReactNode } from "react"
import { Checkbox } from "@/components/ui/checkbox"
import { Field, FieldContent, FieldDescription, FieldLabel } from "@/components/ui/field"

/** One checkbox with its label and an optional hint, as used in lists of things to tick. */
export function CheckboxField({
  label,
  hint,
  checked,
  disabled,
  onChange,
  testId,
}: {
  label: string
  hint?: ReactNode
  checked: boolean
  disabled?: boolean
  onChange: (checked: boolean) => void
  /** The checkbox's test id, for the end-to-end tests (ADR 0023). */
  testId?: string
}) {
  const id = useId()

  return (
    <Field orientation="horizontal" data-disabled={disabled ? true : undefined}>
      <Checkbox
        id={id}
        checked={checked}
        disabled={disabled}
        data-testid={testId}
        onCheckedChange={(value) => onChange(value)}
      />
      <FieldContent>
        <FieldLabel htmlFor={id}>{label}</FieldLabel>
        {hint && (
          <FieldDescription data-testid={testId && `${testId}-hint`}>{hint}</FieldDescription>
        )}
      </FieldContent>
    </Field>
  )
}
