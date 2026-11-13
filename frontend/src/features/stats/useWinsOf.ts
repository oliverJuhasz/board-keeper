import { useTranslation } from "react-i18next"
import { formatShare } from "@/lib/numbers"

/** Gives a function that writes wins out of plays with the win rate, such as "2 of 3 (67%)". */
export function useWinsOf() {
  const { t } = useTranslation()
  return (wins: number, plays: number) =>
    `${t("stats.winsOf", { wins, plays })} (${formatShare(wins / plays)})`
}
