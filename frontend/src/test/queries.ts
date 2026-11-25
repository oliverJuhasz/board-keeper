import { buildQueries, queries, queryAllByTestId, within } from "@testing-library/react"

/**
 * Tests find elements by their test id (ADR 0023). Where several share one, such as a row for
 * each Player, a test tells them apart by what they say, as the end-to-end tests do with
 * Playwright's `filter({ hasText })`. Testing Library has no such filter, so this adds one as a
 * custom query: `buildQueries` turns the function that finds every match into the usual family
 * of get, getAll, query, queryAll, find and findAll.
 * https://testing-library.com/docs/dom-testing-library/api-custom-queries
 */
function queryAllByTestIdWith(container: HTMLElement, testId: string, text: string | RegExp) {
  return queryAllByTestId(container, testId).filter((element) => {
    const said = element.textContent ?? ""
    return typeof text === "string" ? said.includes(text) : text.test(said)
  })
}

const [
  queryByTestIdWith,
  getAllByTestIdWith,
  getByTestIdWith,
  findAllByTestIdWith,
  findByTestIdWith,
] = buildQueries(
  queryAllByTestIdWith,
  (_, testId, text) => `Several elements have the test id "${testId}" and say "${text}".`,
  (_, testId, text) => `No element has the test id "${testId}" and says "${text}".`,
)

// Given queries of its own, `within` binds only those, so the usual ones come along too.
const withText = {
  ...queries,
  queryAllByTestIdWith,
  queryByTestIdWith,
  getAllByTestIdWith,
  getByTestIdWith,
  findAllByTestIdWith,
  findByTestIdWith,
}

/**
 * The whole page, with the usual queries and those above, like Testing Library's `screen`:
 * `page.getByTestIdWith("player-row", "Anna")`.
 */
export const page = within(document.body, withText)

/** An element's insides, with the usual queries and those above, like Testing Library's `within`. */
export function inside(element: HTMLElement) {
  return within(element, withText)
}
