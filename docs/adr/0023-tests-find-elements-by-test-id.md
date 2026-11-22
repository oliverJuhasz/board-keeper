---
status: accepted
---

# Tests find elements by test id

Both the Vitest tests and the Playwright end-to-end tests find what they work with by a `data-testid` attribute, named in the glossary's terms, such as `score-pad` or `placing`. A test should break when what Board Keeper does changes, not when its markup does. A test id stays the same when a class name, a wording, a translation or the kind of element changes, so restyling a page, rewording a button or correcting a Hungarian text breaks no test. An element with a test id is also visibly in use by a test, which leaves the rest of the markup free to change. And Testing Library's `getByRole` is slow in jsdom on large pages, such as the form for a Play with a full Score Pad.

## Considered options

- **Roles and visible text**, such as `getByRole("button", { name: "Sign in" })`, as Testing Library and Playwright advise, and as the Vitest tests did until now. They also check, in passing, that controls have accessible names. Rejected because a test then depends on the English wording, and on which element is used: a link that becomes a button breaks it, though nothing changed for the person using it.
- **CSS selectors and class names.** Rejected: they break whenever the styling changes.

## Consequences

- A test checks text only where the text is what it is about, such as a Player's Placing.
- An ESLint rule forbids the role, text and label queries in both test suites, so the convention holds without anyone watching for it.
- The test ids stay in the built app, because the end-to-end tests run against the container image as it ships.
- Accessibility, which role queries used to check in passing, is checked by axe in the end-to-end tests.
