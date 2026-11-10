# Board Keeper: notes for Claude

Read these before working on anything:

- `README.md`: the chosen stack and deployment model.
- `CONTEXT.md`: the domain glossary.
- `docs/adr/`: decisions already made, and why.
- `docs/roadmap.md`: what is in v1, what is deliberately left out, and the milestones.

## Working with the author

- **Tell the author before moving from discussion to writing code, and wait for their go-ahead.** They may want to switch models first.
- This is a learning project first (ADR 0001). The author knows Java and jOOQ, is rusty with Spring, and is new to React, Tailwind and Flyway. When you introduce something unfamiliar from those areas, briefly explain what it is and why you used it. Prefer idiomatic, current approaches over shortcuts, even where a shortcut would do.
- Decisions belong to the author. When something needs a choice, give your recommendation and ask; don't settle it silently.

## Domain language

- Use the terms in `CONTEXT.md` exactly, everywhere: code, API paths, translation keys, UI text, commits and docs. Never use the words listed under _Avoid_. For example, a single occasion of playing is a `Play`, never a session or match.
- Keep `Setup` (the choices for the whole Play) and `PlayerSetup` (the choices for one Player) distinct.
- If a new domain concept comes up, or an existing term doesn't fit, raise it with the author. Once it's agreed, update `CONTEXT.md`. That file is only a glossary, so implementation details don't go in it.

## Decisions

- Don't silently work against an ADR. If a change conflicts with one, raise it. If the author agrees, write a new ADR and mark the old one `status: superseded by ADR-NNNN`.
- Write an ADR only when a decision is hard to reverse, would surprise a reader without context, *and* came from a real trade-off. One short paragraph is enough. Number them in sequence in `docs/adr/`.

## Repository rules

- **Nothing about a particular installation goes into the repository** (ADR 0009): no hostnames, IP addresses, user or group IDs, tokens or passwords. Read such values from environment variables or an untracked `.env` file, and commit a `.env.example` containing only placeholders. Network access, reverse proxies, automatic updates and backup scheduling for a specific installation are out of scope.
- **Database:** never edit a Flyway migration once it has been applied; add a new one instead. Don't use jOOQ's generated classes inside Java migrations, because they describe the latest schema, not the schema at that migration.
- **Game Modules** depend only on `game-api` and have no database tables of their own. Each has a UUID that never changes plus a readable key (ADR 0012). Each ships one translation file per language under a name of its own, such as `i18n/calico_en.properties`, for English, Dutch and Hungarian. English is required; the others fall back to English.
- **Scoring rules** come from the game's official rulebook. Online summaries often disagree, so when a rule is unclear, ask instead of guessing.

## Build and test

There is no code yet. Milestone M0 creates the skeleton; add the build, test and run commands here when it does.
