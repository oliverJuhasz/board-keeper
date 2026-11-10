# Roadmap

Terms are defined in [`CONTEXT.md`](../CONTEXT.md). The reasons behind the bigger decisions are in [`docs/adr/`](adr/).

## v1 scope

### In v1

- Registering only through an Invite. The first Admin is created through a one-time setup link written to the log.
- Signing in with a password (with long-lived "remember me") and with passkeys.
- Roles and Permissions, with checks on individual items ("edit Plays I recorded", "edit any Play").
- Players, and Claims made through Invites.
- A catalogue of Games, added by hand. Each Game is scored by its own Game Module or by the Generic Module.
- Recording finished Plays:
  - the Setup (Expansions and Variants)
  - each Player's own choices, such as their Spirit or Wonder
  - Recording Mode (Points or Winner only), switchable per Play
  - Solo Plays as each module defines them
  - Cooperative Plays, with an optional group score
  - Tie-breakers (by Score Category or the recorder's choice) and shared Placings
  - Victory Types
- Editing and deleting Plays according to Permissions.
- Recalculation of a single Play, or of every Play of a Game by an Admin.
- Statistics:
  - play history, filterable by "my plays", Game, Player and date
  - a page per Game: plays, wins and win rate per Player, best and average score, average Placing, averages per Score Category
  - a page per Player: plays, wins, win rate, most-played Games
- The interface in English (default), Dutch and Hungarian.
- An installable PWA that works online only.
- A container image built by CI, an example compose file and a self-hosting guide.

### Deliberately not in v1

Recording without a connection, teams, unfinished plays, game nights, importing games from BoardGameGeek, signing in through an outside provider (such as Google), groups or tenants, and Game Modules loaded at runtime.

### Later

- Merging duplicate Players. The model must not rule it out.
- Head-to-head records, streaks and charts.
- Signing in through an outside provider.
- Before publishing the repository: a licence and ARM images.

## Milestones

| | Milestone | Contents | Learning focus |
|---|---|---|---|
| **M0** | Skeleton | Maven modules (`game-api`, `games/generic`, `app`), a Modulith structure test, Flyway with jOOQ code generation from a Testcontainers PostgreSQL, the Vite + React + TypeScript app with Tailwind and shadcn/ui served by Spring Boot, a Dockerfile, CI publishing to GHCR, and an example compose file. One page shows data from the API. | Project setup, end to end |
| **M1** | Accounts and access | Setup link, Invites, password sign-in, Roles and Permissions, Players and Claims, translation setup (en/nl/hu) | Spring Security; React forms and routing |
| **M2** | Recording with the Generic Module | Game catalogue; recording competitive and Cooperative Plays (Points or Winner only); Results and Placings; editing according to Permissions; play history | TanStack Query, generated API types, jOOQ |
| **M3** | The module contract with real games | `StandardGameModule`, Setup, Tie-breakers, solo styles; Calico, then Wyrmspan, then Harmonies | Designing a plug-in interface |
| **M4** | Statistics | Events from `plays`, statistics' own tables, the per-Game and per-Player pages | Spring Modulith events |
| **M5** | Passkeys, PWA, self-hosting guide | Passkey registration and sign-in, installing and updating the app, the guide | Spring Security passkeys; service workers |
| **M6** | More games | 7 Wonders Duel with Pantheon and Agora (Victory Types); Spirit Island with its expansions (per-player choices, number Variants); screens for Recalculation | Stretching the module contract |

## Details to settle when a milestone starts

- **M2:** which fields a Play has besides its Game, date and Players, for example an optional location and notes.
- **M3 and M6:** each game's exact scoring and tie-break rules, taken from its official rulebook. Online summaries disagree, for example on the order of Calico's tie-breakers and on Spirit Island's score formula.
