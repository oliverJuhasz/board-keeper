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

/** A question with a few answers to pick one from, each with an optional hint. */
export function RadioField<Value extends string>({
  legend,
  value,
  onChange,
  options,
}: {
  legend: string
  value: Value
  onChange: (value: Value) => void
  options: { value: Value; label: string; hint?: ReactNode }[]
}) {
  const id = useId()

  return (
    <FieldSet>
      <FieldLegend variant="label">{legend}</FieldLegend>
      <RadioGroup value={value} onValueChange={(chosen) => onChange(chosen as Value)}>
        {options.map((option) => (
          <Field key={option.value} orientation="horizontal">
            <RadioGroupItem id={`${id}-${option.value}`} value={option.value} />
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
