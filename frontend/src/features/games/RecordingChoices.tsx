import { useTranslation } from "react-i18next"
import { RadioField } from "@/components/RadioField"
import { asksWhoWins, type Recording } from "./recordedAs"

/**
 * The questions about how Plays are recorded: competitive or cooperative, points or only the
 * winner, and whether the highest or the lowest score wins. A Game answers them once, as its
 * starting choices, and whoever records a Play can answer them again for that Play.
 */
export function RecordingChoices({
  value,
  onChange,
}: {
  value: Recording
  onChange: (value: Recording) => void
}) {
  const { t } = useTranslation()

  return (
    <>
      <RadioField
        legend={t("recording.kind")}
        testId="recording-kind"
        value={value.cooperative ? "cooperative" : "competitive"}
        onChange={(kind) => onChange({ ...value, cooperative: kind === "cooperative" })}
        options={[
          {
            value: "competitive",
            label: t("recording.competitive"),
            hint: t("recording.competitiveHint"),
          },
          {
            value: "cooperative",
            label: t("recording.cooperative"),
            hint: t("recording.cooperativeHint"),
          },
        ]}
      />
      <RadioField
        legend={t("recording.recording")}
        testId="recording-mode"
        value={value.recordingMode}
        onChange={(recordingMode) => onChange({ ...value, recordingMode })}
        options={[
          {
            value: "POINTS",
            label: t("recording.points"),
            hint: t("recording.pointsHint"),
          },
          {
            value: "WINNER_ONLY",
            label: value.cooperative ? t("recording.wonOrLostOnly") : t("recording.winnerOnly"),
            hint: t("recording.outcomeHint"),
          },
        ]}
      />
      {asksWhoWins(value) && (
        <RadioField
          legend={t("recording.winner")}
          testId="recording-winner"
          value={value.lowestScoreWins ? "lowest" : "highest"}
          onChange={(winner) => onChange({ ...value, lowestScoreWins: winner === "lowest" })}
          options={[
            { value: "highest", label: t("recording.highest") },
            { value: "lowest", label: t("recording.lowest") },
          ]}
        />
      )}
    </>
  )
}
