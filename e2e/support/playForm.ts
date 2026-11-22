import { expect, type Page } from "@playwright/test"

/** What the form for a Play is asked most often. */

/** Opens the form for a Play of a Game, from the start page, as a person would. */
export async function startRecording(page: Page, game: string) {
  await page.goto("/")
  // The link's text is the Game's name, exactly: "7 Wonders" must not find "7 Wonders Duel".
  await page
    .getByTestId("record-play")
    .filter({ hasText: exactly(game) })
    .click()
  await expect(page).toHaveURL(/\/plays\/new\?game=/)
}

/** A pattern that matches this text and nothing longer. */
export function exactly(text: string) {
  return new RegExp(`^${text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}$`)
}

/** Adds Players to the Play, from the list of Players to add. */
export async function addPlayers(page: Page, names: string[]) {
  for (const name of names) {
    await page.getByTestId("players-to-add").selectOption({ label: name })
  }
}

/** The Players' names, as the score pad's columns show them, in their order. */
export function scorePadPlayers(page: Page) {
  return page.getByTestId("score-pad").getByTestId("score-pad-player")
}

/**
 * Fills in the score pad: for each Player, by name, a value for each row, by the key of its Score
 * Category. The pad has a column for each Player, in their order.
 */
export async function fillScorePad(page: Page, scores: Record<string, Record<string, number>>) {
  const columns = await scorePadPlayers(page).allTextContents()
  for (const [name, values] of Object.entries(scores)) {
    const column = columns.indexOf(name)
    expect(column, `${name} has a column on the score pad`).toBeGreaterThanOrEqual(0)
    for (const [category, value] of Object.entries(values)) {
      await page.getByTestId(`score-${category}`).nth(column).fill(String(value))
    }
  }
}

/** The row of a Player in the form's list of Players. */
export function playerRow(page: Page, name: string) {
  return page.getByTestId("player-row").filter({ hasText: name })
}
