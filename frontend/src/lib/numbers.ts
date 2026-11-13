import { formattingLocale } from "./locale"

/** A number such as an average, with at most one decimal: "42.5", or "42,5" in Dutch. */
export function formatAverage(value: number) {
  return new Intl.NumberFormat(formattingLocale(), { maximumFractionDigits: 1 }).format(value)
}

/** A share such as a win rate, as a whole percentage: 2 of 3 is "67%". */
export function formatShare(share: number) {
  return new Intl.NumberFormat(formattingLocale(), {
    style: "percent",
    maximumFractionDigits: 0,
  }).format(share)
}
