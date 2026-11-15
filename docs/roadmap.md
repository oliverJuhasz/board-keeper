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
- An installable PWA. New Plays can be recorded without a connection and are sent later.
- A container image built by CI, an example compose file and a self-hosting guide.
- The API's description and Swagger UI, open to everyone who can reach a running installation.

### Deliberately not in v1

Changing Plays without a connection, teams, unfinished plays, game nights, importing games from BoardGameGeek, signing in through an outside provider (such as Google), groups or tenants, and Game Modules loaded at runtime.

### Later

- Merging duplicate Players. The model must not rule it out.
- Rulebooks for each Game, readable in Board Keeper. Parked during M6 until it is clear how they are best uploaded. They are copyrighted and may only be used privately, so they will be uploaded to each installation, never shipped in the repository or the container image.
- Head-to-head records, streaks and charts.
- Playwright end-to-end tests, once the frontend is functionally complete and settled.
- Signing in through an outside provider.
- Limits on the number of sign-in attempts. Left out while the app sits on a private network.
- Before publishing the repository: ARM images.
- Versioning the API, from the first release on. Until then the API changes along with the frontend, so a script that uses it may need changing after an update.
- An MCP server, so that AI agents can use Board Keeper through tools such as recording a Play. Until then they use the API with an API Key (ADR 0017).

## Milestones

| | Milestone | Contents | Learning focus |
|---|---|---|---|
| **M0** | Skeleton | Maven modules (`game-api`, `games/generic`, `app`), a Modulith structure test, Flyway with jOOQ code generation from a Testcontainers PostgreSQL, the Vite + React + TypeScript app with Tailwind and shadcn/ui served by Spring Boot, a Dockerfile, CI publishing to GHCR, and an example compose file. One page shows data from the API. | Project setup, end to end |
| **M1** | Accounts and access | Setup link, Invites, password sign-in, Roles and Permissions, Players and Claims, translation setup (en/nl/hu) | Spring Security; React forms and routing |
| **M2** | Recording with the Generic Module | Game catalogue; recording competitive and Cooperative Plays (Points or Winner only); Results and Placings; editing according to Permissions; play history | TanStack Query, generated API types, jOOQ |
| **M3** | The module contract with real games | `StandardGameModule`, Setup, Tie-breakers, solo styles; Calico, then Wyrmspan, then Harmonies | Designing a plug-in interface |
| **M4** | Statistics | Events from `plays`, statistics' own tables, the per-Game and per-Player pages | Spring Modulith events |
| **M5** | Passkeys, PWA, self-hosting guide | Passkey registration and sign-in, installing and updating the app, the guide | Spring Security passkeys; service workers |
| **M6** | More games | Hadara; 7 Wonders Duel with Pantheon and Agora (Expansions, Victory Types); 7 Wonders with Leaders and Cities (Player Setup, negative Score Categories); Spirit Island without its expansions (number Variants, a calculated group score); screens for Recalculation | Stretching the module contract |

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
- Editing a Play can change everything that was entered for it, and scores it again. Its Game cannot be changed, and who recorded it and when stay as they were.
- A deleted Play is gone for good, after an "are you sure?".
- The play history is a page of its own in the navigation. The Games page stays the start page for now; this is looked at again when statistics arrive in M4.
- The history can be narrowed to "my plays" (Plays I took part in), a Game, a Player, and a period. It shows 50 Plays at a time, newest first, with a button to show more.
- M2 is delivered in three slices: the Game catalogue, recording a Play, and the play history.

## Settled for milestone M3

- A Game with a Game Module of its own appears in the catalogue by itself. It is shown under the name its module gives it in each language, and can be neither edited nor removed.
- A Game that was added by hand under the name of a module's Game is handed to that module when the module arrives. Its earlier Plays stay as recorded, with totals only, and new Plays use the module's score pad.
- A module describes its score pad: how many can play, its Variants, its Score Categories and its Tie-breakers. One general form in the frontend draws it, so adding a game needs no frontend code.
- The order of work is the describing side of the contract with Calico, then Wyrmspan, then Harmonies. Solo styles come with the first of these games that needs one.
- The form that records a Play offers adding a new Player, to Accounts that may create Players.
- Calico is scored from the publisher's rulebook (page 8): design goals, cats and buttons add up, and a tie goes to the most cat tokens, then the most button tokens, then is shared. The number of cat tokens is not on the score pad, so the form asks who won a tie. The family variant is recorded as a Variant. The beginner setup and the lower variance variant for two players change nothing about the scoring and are not recorded.
- Harmonies is scored from the publisher's rulebook (pages 7 to 10): Trees, Mountains, Fields, Buildings, Water and the Animal cards add up, and a tie goes to the most Animal cubes placed, then is shared. The number of cubes is not on the score pad, so the form asks who won a tie. The side of the Personal boards is a Variant: side A scores the River, side B the Islands. The Nature's Spirit cards are a Variant that adds a row. The paper pad's line per Animal card is one row here, for their sum.
- Wyrmspan is scored from the publisher's rulebook (page 15), its score pad and the Automa rulebook (page 5): eight rows, and a tie goes to the most visible dragons on the player mat, then is shared. The number of dragons is not on the score pad, so the form asks who won a tie.
- **Solo Plays.** A Game's module says whether it can be played alone, and how. Calico and Harmonies are played alone for score only: the Play has a total, no Placing, and is neither won nor lost. Their scenarios and suns are not recorded. Wyrmspan is played alone against the Automa: the Automa's score is a row of the score pad, the Play is won by beating it, and a tie is won. The Automa's level and Ravel Mode are Variants of Solo Plays only.
- What a module describes is called the Score Pad, after the rulebooks; it is in the glossary.
- **The form asks who won a tie only when the scores can't say.** A Game Module names its Tie-breakers. Where a Tie-breaker is something that was entered, such as the money each Player has left in a Game where money decides a tie, the module decides the tie itself and the form does not ask. The form asks only when the Game decides a tie by something that isn't recorded, such as who played the fewest cards, and only if the Players are still tied after the Tie-breakers the module can work out. The Generic Module records nothing but totals, so with it the form always asks.

## Settled for milestone M4

- **Recording a Play takes as few steps as possible from opening Board Keeper**, because that is what it is for. The start page offers the last few Plays the signed-in Player took part in to play again: one tap opens the form with the same Game, Players and choices, so only the date and the scores are left. Below them come the Games, the most recently played first, and a tap anywhere on a Game opens its form.
- The Games page stays the start page. A tap on a Game there opens the form, so its statistics are in each Game's menu instead, and the Game's name on a Play's page leads to them too. Each Player's name on the Players page leads to theirs.
- Solo Plays don't count toward win rates or average Placing. The pages of a Game and a Player show them in a section of their own: how many, the best and average score, and the wins against an automated opponent.
- A Play recorded with only the winner counts in plays, wins and win rate, and is left out of score averages and average Placing.

## Settled for milestone M5

- **A sole Admin who has forgotten their password** gets back in with a command run on the container. It writes a one-time Reset Link for the Account to the log, the way the first-time setup link is written, and stops again. Only someone who can run commands on the container can use it.
- **The frontend's code is split by page**, before the app can be installed on a phone. Each page is loaded the first time it is needed, so the first load stops growing with every new page, and after an update a browser downloads only the files that changed. The first load today is still about 270 kB compressed: most of it is the libraries the start page needs, such as React, the router and the form libraries, and the start page leads straight to the form for a Play.

- **Passkeys are an extra way to sign in.** Every Account keeps its password, so a lost phone costs nothing: sign in with the password and remove that passkey in Settings. Passkeys are added, named and removed in Settings. The sign-in page has a "Sign in with a passkey" button, and where the browser can, it also suggests passkeys in the username field. A passkey belongs to one web address, so an installation offers passkeys only once its address is set (BOARDKEEPER_URL).
- **Installing the app on a phone** uses vite-plugin-pwa, which generates the service worker (the script that keeps the app's files on the phone). A new version is announced with a Reload button; the app never reloads by itself, so a half-filled form is never lost.
- **New Plays can be recorded offline**, and are sent once there is a connection. Existing Plays can't be changed offline, so two devices never change the same Play differently. The phone gives each Play it records its UUID, and the server records a Play with a given UUID only once, so sending again after a lost answer does no harm. Offline, the form has no totals, Placings or tie questions, because the server works those out; a Play the server refuses when it is sent waits on the phone, marked for attention, until it is corrected. New Players can't be added offline. Plays wait until the Account that recorded them is signed in.

## Settled for milestone M6

- **The order of work** is Hadara, then 7 Wonders Duel, 7 Wonders and Spirit Island, and last the screens for Recalculation.
- **Rulebooks are parked** (see Later). The publishers' rulebooks are copyrighted and may only be used privately, so when they come, an installation's rulebooks will be uploaded to it and kept in its database, never shipped in the repository or the container image. How they are uploaded is still open.
- **Spirit Island is played without its expansions** for now: the base game's Spirits, adversaries and scenarios.
- **7 Wonders is the second edition** (2020), with the Leaders and Cities expansions of that edition.
- **Expansions and Victory Types** come with 7 Wonders Duel. A Game's module lists the Expansions a Play can be played with, and the form offers them as checkboxes, apart from the Variants; they are part of the Play's Setup. A Game that can be won in more than one way has Victory Types, and the form asks how each Play was won. A Victory Type not on points, such as a supremacy, ends the Play before anyone counts points: the form asks only for its one winner. A Play's page and the play history name the Victory Type, and a Game's statistics count its Plays by Victory Type.
- 7 Wonders Duel is scored from the publisher's rulebooks: the base game (pages 12 and 13), Pantheon (pages 7 and 10) and Agora (page 13). A Play is won by a civilian victory on points, or by military or scientific supremacy, or with Agora by political supremacy. On points, the rows of the score pads add up; a tie goes to the most points from the blue Civilian Buildings, then is shared, and both are on the pad, so the form never asks. Pantheon returns the Guilds to the box and puts Grand Temples in their place, and adds a row for its Divinities. Agora adds a row for the Senate. On the expansions' score pad, the fourth row is read as the Guilds (or the Grand Temples with Pantheon) and the fifth as the Divinities. The promotional Wonders change nothing on the pad.
- **Player Setup** comes with 7 Wonders: a Game's module lists what is chosen for each Player, such as their Wonder board and its side, and the form offers a drop-down for each in the Player's row. Every choice may be left open, so a forgotten board never stops a Play from being recorded. Where each option exists once, as each Wonder board does, two Players can't have the same one. A Play's page shows each Player's choices.
- 7 Wonders is scored from the publisher's rulebooks: the base game (page 8), Leaders (page 4) and Cities (pages 3 and 5), and their score pads. It is for three to seven players, by the box. The Wonder, Treasury, Military, blue, yellow, green and purple rows add up; Leaders adds a Leaders row and Cities a row for the black cards. Military can be negative, and so can the Treasury, because Cities counts Debts on that line. A tie goes to the most coins, then is shared; the coins are not on the score pad, and ties are rare, so the form asks who won one instead of asking everyone for their coins. The Player Setup is the Wonder board (the seven of the base game, Roma and Abu Simbel from Leaders, Byzantium and Petra from Cities, and the Manneken Pis promo) and its Day or Night side. Cities' team game and the Armada expansion are not supported.
- **Cooperative Games with modules of their own** come with Spirit Island. The module says the Game is cooperative and which Recording Mode a Play starts with. Recorded in points, the group's rows are filled in and the module works out the group's score; the form shows it as the rows are filled in.
- **A Variant can be a whole number in a range**, such as an adversary's level, and can depend on another choice of the Setup: the level is only asked once the adversary is chosen.
- Spirit Island is scored from the publisher's rulebook, without its expansions: "Scoring" on page 23 and the difficulty chart on page 28. It is cooperative, for one to four players, each with a Spirit of their own as their Player Setup. A Play starts with only whether the Spirits won, because scoring is optional. With a score, after a victory: 5 × Difficulty + 10 + 2 per Invader card left in the deck; after a defeat: 2 × Difficulty + 1 per Invader card no longer in the deck; either way, + 1 per X living Dahan and − 1 per X Blight, X being the number of players. The rulebook doesn't say how to round "per X", and no ruling could be found online, so only complete groups of X count. The Difficulty is the adversary's at its level (base level 0, or 1 to 6), plus the scenario's, plus 3 for the thematic map. The Invader cards row says which cards to count, after a win and after a loss.
- Hadara is scored from the publisher's rulebook (page 10) and its solo rules (pages 2 and 3): Colonies, Statues, Silver medals, Gold medals, Cards and Remaining coins add up, and a tie goes to the most coins left over, then is shared. The leftover coins are not on the score pad, so the form asks who won a tie. A Solo Play is played against the NPC: its score is a row of the score pad, and the Play is won by beating it. A tie is won too: the solo rules follow the normal rules, and the NPC never has coins, so it can't win a tie. The NPC's level (1 to 11, or God mode) is a Variant of Solo Plays only. Which side of the setup cards was used changes nothing about the scoring and is not recorded.
- **Recalculation** runs the Game Module over what was entered for a Play again, and replaces its Results if they came out differently. One Play is recalculated from its page, by whoever may change it. Every Play of a Game is recalculated from the Game's page, by an Admin only: the Permission for it is admin-only, so no other Role can be given it. Neither shows a preview first; afterwards, a Play's page says whether its Results changed, and a Game's page says how many Plays it went through, how many changed, and which ones the module refuses now. A refused Play keeps the Results it had, and links to its page so it can be corrected.

## Details to settle when a milestone starts

- **M3 and M6:** each game's exact scoring and tie-break rules, taken from its official rulebook. Online summaries disagree, for example on the order of Calico's tie-breakers and on Spirit Island's score formula.
