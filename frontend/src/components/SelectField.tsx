import { useId, type ReactNode } from "react"
import { Field, FieldDescription, FieldLabel } from "@/components/ui/field"
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select"

/**
 * A question with many answers to pick one from, as a drop-down: where a RadioField would make
 * a long list, such as a level from 1 to 11. It uses the browser's own drop-down, which phones
 * show as their familiar picker.
 */
export function SelectField<Value extends string>({
  label,
  hint,
  value,
  onChange,
  options,
  testId,
}: {
  label: string
  hint?: ReactNode
  value: Value
  onChange: (value: Value) => void
  options: { value: Value; label: string }[]
  /** The drop-down's test id, for the end-to-end tests (ADR 0023). */
  testId?: string
}) {
  const id = useId()

  return (
    <Field>
      <FieldLabel htmlFor={id}>{label}</FieldLabel>
      <NativeSelect
        id={id}
        value={value}
        data-testid={testId}
        aria-describedby={hint ? `${id}-hint` : undefined}
        onChange={(event) => onChange(event.target.value as Value)}
      >
        {options.map((option) => (
          <NativeSelectOption key={option.value} value={option.value}>
            {option.label}
          </NativeSelectOption>
        ))}
      </NativeSelect>
      {hint && <FieldDescription id={`${id}-hint`}>{hint}</FieldDescription>}
    </Field>
  )
}
