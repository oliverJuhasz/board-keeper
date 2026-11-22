import AxeBuilder from "@axe-core/playwright"
import { expect, type Page } from "@playwright/test"

/**
 * Checks the page as it is now with axe, which looks for what makes a page hard to use with a
 * screen reader or a keyboard, or hard to read: a button without a name, a field without a label,
 * too little contrast. A test fails on what axe calls serious or critical (ADR 0023).
 */
export async function expectAccessible(page: Page) {
  const { violations } = await new AxeBuilder({ page }).analyze()
  const problems = violations
    .filter((violation) => violation.impact === "serious" || violation.impact === "critical")
    .map((violation) => ({
      problem: `${violation.id}: ${violation.help}`,
      where: violation.nodes.map((node) => node.target.join(" ")),
    }))
  expect(problems, "accessibility problems").toEqual([])
}
