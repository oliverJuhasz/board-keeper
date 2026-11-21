import { useTranslation } from "react-i18next"
import type { ImportRefusedPlay } from "@/api/client"
import { explainProblem } from "@/api/problems"
import { useGameModuleTexts } from "@/features/games/useGameModuleTexts"
import { formatDay } from "@/lib/dates"

/** The Plays of an Export that this Installation can't take, each with why. */
export function RefusedPlays({ refused }: { refused: ImportRefusedPlay[] }) {
  const { t } = useTranslation()
  const texts = useGameModuleTexts()

  // The reason is the name of a text: a Game Module's own, one of the frontend's, or a problem's
  // code. A Game this Board Keeper can't score names the module it would need.
  const reasonOf = (play: ImportRefusedPlay) =>
    play.reason === "imports.gameModuleMissing"
      ? t("problems.imports.gameModuleMissing", { module: play.gameModuleKey })
      : explainProblem(t, { code: play.reason, reason: play.reason }, texts)

  return (
    <div className="space-y-1">
      <p>{t("exportAndImport.import.refused", { count: refused.length })}</p>
      <ul className="list-disc space-y-1 pl-5">
        {refused.map((play) => (
          <li key={play.playId}>
            {t("exportAndImport.import.refusedPlay", {
              day: formatDay(play.playedOn),
              game: play.game,
              reason: reasonOf(play),
            })}
          </li>
        ))}
      </ul>
    </div>
  )
}
