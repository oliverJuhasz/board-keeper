import { useId, type ReactNode } from "react"
import {
  Field,
  FieldContent,
  FieldDescription,
  FieldLabel,
  FieldLegend,
  FieldSet,
} from "@/components/ui/field"
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group"

/**
 * A question with a few answers to pick one from. The question and each answer can have a hint.
 */
export function RadioField<Value extends string>({
  legend,
  hint,
  value,
  onChange,
  options,
  testId,
}: {
  legend: string
  hint?: ReactNode
  value: Value
  onChange: (value: Value) => void
  /**
   * The answers. Each answer's radio button has the test id `<testId>-<value>`, unless it names
   * one of its own: a test can't know a value such as a Player's id.
   */
  options: { value: Value; label: string; hint?: ReactNode; testId?: string }[]
  /** The question's test id, for the end-to-end tests (ADR 0023). */
  testId?: string
}) {
  const id = useId()

  return (
    <FieldSet data-testid={testId}>
      <FieldLegend variant="label">{legend}</FieldLegend>
      {hint && <FieldDescription data-testid={testId && `${testId}-hint`}>{hint}</FieldDescription>}
      <RadioGroup value={value} onValueChange={(chosen) => onChange(chosen as Value)}>
        {options.map((option) => (
          <Field key={option.value} orientation="horizontal">
            <RadioGroupItem
              id={`${id}-${option.value}`}
              value={option.value}
              data-testid={option.testId ?? (testId && `${testId}-${option.value}`)}
            />
            <FieldContent>
              <FieldLabel htmlFor={`${id}-${option.value}`}>{option.label}</FieldLabel>
              {option.hint && <FieldDescription>{option.hint}</FieldDescription>}
            </FieldContent>
          </Field>
        ))}
      </RadioGroup>
    </FieldSet>
  )
}
