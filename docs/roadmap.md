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
  - each Player's Player Setup, such as their Spirit or Wonder
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
- The API's description and Swagger UI, open to everyone who can reach a running installation.

### Deliberately not in v1

Recording without a connection, teams, unfinished plays, game nights, importing games from BoardGameGeek, signing in through an outside provider (such as Google), groups or tenants, and Game Modules loaded at runtime.

### Later

- Merging duplicate Players. The model must not rule it out.
- Head-to-head records, streaks and charts.
- Playwright end-to-end tests, once the frontend is functionally complete and settled.
- Signing in through an outside provider.
- Limits on the number of sign-in attempts. Left out while the app sits on a private network.
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
| **M6** | More games | 7 Wonders Duel with Pantheon and Agora (Victory Types); Spirit Island with its expansions (Player Setup, number Variants); 7 Wonders with Leaders and Cities (Player Setup, negative Score Categories); screens for Recalculation | Stretching the module contract |

## Settled for milestone M2

- A Play records its Game, its Players and their Results, a date without a time, an optional location as free text, and optional notes.
- Changing a Play, which means editing or deleting it, has three Permissions: for Plays you recorded, for Plays you took part in, and for any Play. A Member gets the first two.
- With the Generic Module, each Game stores how its Plays are recorded by default: competitive or Cooperative, Points or Winner only, and whether the highest or the lowest score wins. The recorder can change these for a single Play. For a tie, the recorder picks who won it or leaves the Placing shared.
- Scores are whole numbers and can be negative.
- A competitive Play recorded with only the winner has one or more winners. Nobody in it gets a Placing.
- The recorder's choice in a tie applies to first place only. Players tied further down share their Placing.
- With the Generic Module, a competitive Play needs at least two Players. A Solo Play can be recorded as Cooperative: won or lost, with an optional score. Solo Plays of competitive Games wait for the solo styles of M3.
- A Play can be dated today or earlier.
- A location is at most 100 characters, and notes at most 2000.
- The form puts whoever records the Play in as its first Player, and they can be taken out again.
- For a tie for first place, the form offers one Player or a shared win. The API also accepts several marked Players, who then share first place ahead of the others tied with them.
- M2 is delivered in three slices: the Game catalogue, recording a Play, and the play history.

## Details to settle when a milestone starts

- **M3:** making a Game with a module of its own appear in the catalogue by itself, named by its module. Until the first such module exists, every Game is added by hand and scored by the Generic Module.
- **M3:** how the frontend builds the form for a Game with a module of its own. Today's form knows the Generic Module's two Variants and its one Score Category; a real module has to describe its own.
- **M3:** whether to offer adding a new Player from the form that records a Play. Today that is done on the Players page first.
- **M3:** what happens to a Game that was added by hand when a module for it arrives later. Its earlier Plays stay as the Generic Module recorded them.
- **M5:** how a sole Admin who has forgotten their password gets back in. Today that needs a second Admin to create a Reset Link. The self-hosting guide needs an answer for when there is none.
- **M5:** splitting the frontend's JavaScript by page. It has grown to about 250 kB compressed, all loaded up front.
- **M3 and M6:** each game's exact scoring and tie-break rules, taken from its official rulebook. Online summaries disagree, for example on the order of Calico's tie-breakers and on Spirit Island's score formula.
- **M6:** which edition of 7 Wonders the module covers, because the first and second editions differ. Any team rules in Cities stay unsupported while teams are out of scope.
