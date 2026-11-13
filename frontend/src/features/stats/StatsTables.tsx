import { cn } from "cn"
import type { ReactNode } from "react"
import { useTranslation } from "react-i18next"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { formatAverage, formatShare } from "@/lib/numbers"

// The tables the statistics pages share. A Game's page has a row for each Player, and a
// Player's page a row for each Game; the columns are the same.

/** What a table shows where there is no number, such as a best score when none was recorded. */
const NONE = "–"

/**
 * The first column, with the names. Where a phone is too narrow for every column, as with long
 * words in Dutch or Hungarian, the table scrolls sideways and this column stays put ("sticky"),
 * so each row still shows whose numbers they are. Its background hides what scrolls under it.
 */
const LABEL_COLUMN = "max-sm:sticky max-sm:left-0 max-sm:z-10 max-sm:bg-background"

/** How someone did in the Plays with others, as one row of a table. */
export type ResultsRow = {
  key: string
  /** What the row is about: a Player's or a Game's name, usually a link. */
  label: ReactNode
  plays: number
  wins: number
  bestScore?: number
  averageScore?: number
  averagePlacing?: number
}

/** How someone did in the Solo Plays, as one row of a table. */
export type SoloRow = {
  key: string
  label: ReactNode
  plays: number
  wins: number
  playsAgainstOpponent: number
  bestScore?: number
  averageScore?: number
}

/**
 * Wins, best and average score, and average Placing. A phone has little room for the columns,
 * so their headings may wrap, and the win rate sits under the wins instead of in a column of
 * its own. Columns without a single number, as in a Game recorded with only the winner, are
 * left out.
 */
export function ResultsTable({
  title,
  labelHeading,
  rows,
}: {
  title: string
  labelHeading: string
  rows: ResultsRow[]
}) {
  const { t } = useTranslation()
  const scored = rows.some((row) => row.bestScore !== undefined)
  const placed = rows.some((row) => row.averagePlacing !== undefined)

  return (
    <section className="space-y-2">
      <h2 className="text-sm font-medium">{title}</h2>
      <Table aria-label={title}>
        <TableHeader>
          <TableRow>
            <TableHead className={cn(LABEL_COLUMN, "text-xs sm:text-sm")}>{labelHeading}</TableHead>
            <NumberHead>{t("stats.columns.wins")}</NumberHead>
            {scored && <NumberHead>{t("stats.columns.bestScore")}</NumberHead>}
            {scored && <NumberHead>{t("stats.columns.averageScore")}</NumberHead>}
            {placed && <NumberHead>{t("stats.columns.averagePlacing")}</NumberHead>}
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((row) => (
            <TableRow key={row.key}>
              <TableCell className={cn(LABEL_COLUMN, "font-medium whitespace-normal")}>
                {row.label}
              </TableCell>
              <TableCell className="text-right tabular-nums">
                {t("stats.winsOf", { wins: row.wins, plays: row.plays })}
                <span className="block text-xs text-muted-foreground">
                  {formatShare(row.wins / row.plays)}
                </span>
              </TableCell>
              {scored && <NumberCell value={row.bestScore} />}
              {scored && <NumberCell value={row.averageScore} />}
              {placed && <NumberCell value={row.averagePlacing} />}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </section>
  )
}

/**
 * The Solo Plays, which count nowhere else. They are won or lost only against an automated
 * opponent; played for a score only, they have no wins to show.
 */
export function SoloTable({ labelHeading, rows }: { labelHeading: string; rows: SoloRow[] }) {
  const { t } = useTranslation()
  const againstOpponent = rows.some((row) => row.playsAgainstOpponent > 0)
  const scored = rows.some((row) => row.bestScore !== undefined)

  return (
    <section className="space-y-2">
      <h2 className="text-sm font-medium">{t("stats.solo")}</h2>
      <Table aria-label={t("stats.solo")}>
        <TableHeader>
          <TableRow>
            <TableHead className={cn(LABEL_COLUMN, "text-xs sm:text-sm")}>{labelHeading}</TableHead>
            <NumberHead>{t("stats.columns.plays")}</NumberHead>
            {againstOpponent && <NumberHead>{t("stats.columns.wins")}</NumberHead>}
            {scored && <NumberHead>{t("stats.columns.bestScore")}</NumberHead>}
            {scored && <NumberHead>{t("stats.columns.averageScore")}</NumberHead>}
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((row) => (
            <TableRow key={row.key}>
              <TableCell className={cn(LABEL_COLUMN, "font-medium whitespace-normal")}>
                {row.label}
              </TableCell>
              <TableCell className="text-right tabular-nums">{row.plays}</TableCell>
              {againstOpponent && (
                <TableCell className="text-right tabular-nums">
                  {row.playsAgainstOpponent > 0
                    ? t("stats.winsOf", { wins: row.wins, plays: row.playsAgainstOpponent })
                    : NONE}
                </TableCell>
              )}
              {scored && <NumberCell value={row.bestScore} />}
              {scored && <NumberCell value={row.averageScore} />}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </section>
  )
}

/**
 * The heading of a column of numbers. On a phone it is smaller and may wrap, so that there is
 * room for the columns.
 */
function NumberHead({ children }: { children: string }) {
  return (
    <TableHead className="text-right text-xs whitespace-normal sm:text-sm">{children}</TableHead>
  )
}

/** A number in a table: whole numbers as they are, averages with at most one decimal. */
export function NumberCell({ value }: { value?: number }) {
  return (
    <TableCell className="text-right text-muted-foreground tabular-nums">
      {value === undefined ? NONE : formatAverage(value)}
    </TableCell>
  )
}
