import { useTranslation } from "react-i18next"
import type { Game } from "@/api/client"

/** How Plays are recorded: a Game's starting choices, or the choices made for one Play. */
export type Recording = Pick<Game, "cooperative" | "recordingMode" | "lowestScoreWins">

/** "Who wins, the highest or the lowest score?" only means something when players compete on points. */
export function asksWhoWins(recording: Recording) {
  return !recording.cooperative && recording.recordingMode === "POINTS"
}

/** Gives a function that says how Plays are recorded in a few words, such as "Points, highest wins". */
export function useRecordedAs() {
  const { t } = useTranslation()

  return (recording: Recording) => {
    if (recording.cooperative) {
      return recording.recordingMode === "POINTS"
        ? t("recording.as.cooperativeScore")
        : t("recording.as.cooperativeWonLost")
    }
    if (recording.recordingMode === "WINNER_ONLY") {
      return t("recording.as.winnerOnly")
    }
    return recording.lowestScoreWins
      ? t("recording.as.pointsLowest")
      : t("recording.as.pointsHighest")
  }
}
