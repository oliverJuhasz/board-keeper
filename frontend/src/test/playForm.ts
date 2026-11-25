import { waitFor } from "@testing-library/react"
import { inside, page } from "./queries"

/**
 * Finding things in the form for a Play, which many tests fill in. Its score pad has a column for
 * each Player and a field for each Score Category in it, all with the test id `score-<category>`:
 * the column's place among the Players says whose field it is.
 */

/** The row of a Player in the form's list of Players. */
export function playerRow(name: string) {
  return inside(page.getByTestIdWith("player-row", name))
}

/**
 * The option of a drop-down with this value, such as a Wonder board's key, or undefined if the
 * drop-down doesn't offer it.
 */
export function optionOf(select: HTMLElement, value: string) {
  return Array.from((select as HTMLSelectElement).options).find((option) => option.value === value)
}

/** The Players' names, as the score pad's columns show them, in their order. */
export function padPlayers() {
  return page.queryAllByTestId("score-pad-player").map((column) => column.textContent)
}

/**
 * What a row at the foot of the score pad says, a cell for each Player: the totals, the Placings,
 * or the result of a Solo Play against an opponent.
 */
export function padFoot(row: "total" | "placing" | "solo-result") {
  return page.queryAllByTestId(row).map((cell) => cell.textContent)
}

/**
 * A Player's field for a Score Category on the score pad, or null. Without a category, the one
 * field beside their name, for a Game whose pad has a single row.
 */
export function queryScoreField(name: string, category?: string): HTMLElement | null {
  if (category === undefined) {
    const row = page.queryByTestIdWith("player-row", name)
    return row && inside(row).queryByTestId("score")
  }
  const column = padPlayers().indexOf(name)
  return column < 0 ? null : (page.queryAllByTestId(`score-${category}`)[column] ?? null)
}

/** Like queryScoreField, but fails if there is no such field. */
export function scoreField(name: string, category?: string): HTMLElement {
  const field = queryScoreField(name, category)
  if (!field) {
    throw new Error(`${name} has no field for ${category ?? "their score"}.`)
  }
  return field
}

/** Like scoreField, but waits for the field to appear. */
export function findScoreField(name: string, category?: string): Promise<HTMLElement> {
  return waitFor(() => scoreField(name, category))
}
