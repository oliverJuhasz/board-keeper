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
}: {
  label: string
  hint?: ReactNode
  checked: boolean
  disabled?: boolean
  onChange: (checked: boolean) => void
}) {
  const id = useId()

  return (
    <Field orientation="horizontal" data-disabled={disabled ? true : undefined}>
      <Checkbox
        id={id}
        checked={checked}
        disabled={disabled}
        onCheckedChange={(value) => onChange(value)}
      />
      <FieldContent>
        <FieldLabel htmlFor={id}>{label}</FieldLabel>
        {hint && <FieldDescription>{hint}</FieldDescription>}
      </FieldContent>
    </Field>
  )
}
