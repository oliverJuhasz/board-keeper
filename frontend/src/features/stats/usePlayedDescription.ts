import { useTranslation } from "react-i18next"
import { formatRecentDay } from "@/lib/dates"

/**
 * Gives a function for the line under a statistics page's title: "Played 3 times · Last played
 * yesterday", or the text given for when nothing was played yet.
 *
 * @param page whose statistics they are. Hungarian says "played" differently of a Game, which
 *     the group played, and of a Player, who played.
 */
export function usePlayedDescription(page: "game" | "player") {
  const { t } = useTranslation()

  return ({ plays, lastPlayedOn }: { plays: number; lastPlayedOn?: string }, notYet: string) =>
    lastPlayedOn
      ? [
          t(`stats.${page}.played`, { count: plays }),
          t("games.lastPlayed", { when: formatRecentDay(lastPlayedOn) }),
        ].join(" · ")
      : notYet
}
