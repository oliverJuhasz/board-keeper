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
- **Tables:** a backend module uses only its own tables and asks other modules' services for anything else. Register every new table in `TableOwnershipTests`.
- **Database:** never edit a Flyway migration once it has been applied; add a new one instead. Don't use jOOQ's generated classes inside Java migrations, because they describe the latest schema, not the schema at that migration.
- **Game Modules** depend only on `game-api` and have no database tables of their own. Each has a UUID that never changes plus a readable key (ADR 0012). Each ships one translation file per language under a name of its own, such as `i18n/calico_en.properties`, for English, Dutch and Hungarian. English is required; the others fall back to English. A module describes its Game in `scorePad()` and has an English text for everything the pad names; `docs/development.md` lists the names. Its scoring depends only on the entry it is given, and every reason of its own for refusing an entry has a text named `<key>.invalid.<reason>`. Once a module is released, the keys its score pad uses never change and are never removed, because stored Plays and Exports hold them. If one must change, the module keeps accepting the old one.
- **Access:** everything under `/api` needs a signed-in Account unless `SecurityConfiguration` opens it. Guard what needs a Permission with `@RequiresPermission`, and hide it in the frontend with `useCan()`. Where the answer depends on the item, as with changing a Play, the service decides (`Plays.mayChange`) and the API tells the frontend in the item itself (`canChange`). Report failures by throwing a `Problem` with a code, and add its text under `problems` in all three translation files. Anything that affects another Account's access (its Roles, its password, whether it can sign in) is for Admins only (ADR 0015).
- **Pages:** build new frontend pages from the building blocks listed in `docs/development.md` (`PageHeader`, `QueryState`, `ActionsMenu`, `ConfirmDialog` and the form fields) before writing new ones.
- **Texts:** nothing the user reads is written into a component. Add every text to all three files in `frontend/src/i18n/locales/` (or, for a Game Module, to its `i18n` files) and write the Dutch and Hungarian yourself, informally, for the author to correct.
- **Changelog:** every change that someone who uses or runs Board Keeper would notice adds a line under Unreleased in `CHANGELOG.md`, in the same pull request: in English, in the glossary's terms, under Added, Changed, Deprecated, Removed, Fixed or Security. Refactoring, tests and internal docs stay out. A release is a git tag, and no file carries the version (ADR 0018); `docs/development.md` has the steps.
- **Scoring rules** come from the game's official rulebook. Online summaries often disagree, so when a rule is unclear, ask instead of guessing.

## Build and test

`docs/development.md` has the full guide. In short:

- Building needs JDK 25, Node.js 22.12 or later, and a running Docker daemon.
- Backend, in `backend/`: `./mvnw verify` generates the jOOQ code and runs every test. `./mvnw spring-boot:test-run` runs the app on port 8080 with a throwaway PostgreSQL and a development Admin (`admin` / `correct horse battery staple`).
- Frontend, in `frontend/`: `npm ci`, then `npm test`, `npm run lint`, `npm run typecheck`, `npm run format:check`, `npm run build`. `npm run dev` serves it on port 5173 and passes `/api` on to the backend.
- The jar includes the frontend only if `frontend/dist` exists, so run `npm run build` before `./mvnw verify` when building the whole application or the image.
- After changing the API, run `./mvnw verify -Dboardkeeper.openapi.update=true` and commit `api/openapi.json`. A test fails while that file doesn't match the API.
- Add shadcn/ui components with `npx shadcn@latest add <name>`, then `npm run format`.
- Before calling a change done, run the backend and frontend checks above, and for anything visible, look at it in a browser.
