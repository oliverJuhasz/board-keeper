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
- Signing in through an outside provider.
- Limits on the number of sign-in attempts. Left out while the app sits on a private network.
- Before publishing the repository: ARM images.
- Versioning the API, before Board Keeper is published. Until then the API changes along with the frontend, so a script that uses it may need changing after an update.
- Moving to Maven 4 once it is released. Its new pom format no longer needs each module to repeat its parent's version.
- An MCP server, so that AI agents can use Board Keeper through tools such as recording a Play. Until then they use the API with an API Key (ADR 0017).
- An Export of part of the record, narrowed as the play history is: to a Game, a Player, a period or "my plays".
- A CSV view of an Export, to open in a spreadsheet. Only the JSON file can be imported.

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

## Settled after 1.0

- **Expansions declare what they change** on the Score Pad ([ADR 0019](adr/0019-expansions-declare-what-they-change.md)). An Expansion can let more Players take part, and a Player Setup option can come with an Expansion. The form asks for the Expansions before the Players and follows them: it offers an Expansion's options only once it is ticked, and an option chosen before the Expansion was taken off stays, marked as a problem. Lowering the fewest Players, and ways of playing alone that come with an Expansion, wait for a Game that needs them. 7 Wonders' Roma and Abu Simbel come with Leaders, and Byzantium and Petra with Cities.
- **A Cooperative Game can score only a victory.** Its rows are then filled in after a victory only, and a defeat has no score.
- **A choice of a Setup can start at an option other than its first**, so its options can stay in their natural order.
- Last Bastion is scored from the publisher's rulebook: "End of the game" and "Score" on page 10, "Games with less than 4 players" and "Difficulty levels" on page 11, and the Heroes of the Power Description booklet. It is cooperative, for one to four players, each with a Hero of their own as their Player Setup. The Difficulty (Novice, Normal, Difficult or Heroic) is a Variant, Normal by default, as the rulebook suggests it for the first games. A Play starts in points, because the box has a pad of score sheets, but only a victory is scored: 5, 10, 15 or 20 points by Difficulty, + 1 per Life token, + 2 per Call to Arms token, − 3 per dead hero and − 4 per Bastion tile in the Grasp of Evil. A third tile in the Grasp of Evil loses the game, so a victory has at most two, and a dead hero never comes back, so a victory has fewer dead heroes than players.
- Lords of Waterdeep is scored from the publishers' rulebooks: the base game's "Final Scoring" on page 11 and its Lords on pages 20 and 21, and Scoundrels of Skullport's pages 4, 5 and 8. It is for two to five players. The two halves of Scoundrels of Skullport, Undermountain and Skullport, are Expansions of their own, and either lets six play. There is no paper score pad, so the rows follow "Final Scoring": the Victory Points on the scoring track, which can be below zero, the Adventurers (1 each), the Gold (1 per 2) and the Lord card. Skullport adds a row for Corruption, the points lost: each token costs the value of the empty space of the Corruption track farthest from −1, as page 8 and its example say; the rules reference on its back cover says "closest". A tie goes to the most Gold: the points for Gold decide it where they differ, and the recorder is asked about the rest. Each player's Lord of Waterdeep is their Player Setup: the base game's eleven, and three more with each half, which only that half offers. The faction and the long game change nothing about the scoring and are not recorded.

## Settled for import and export

An Installation's Games, Players and Plays can be saved as an Export, for use elsewhere, and an Export can be brought into another Installation by an Import. Importing from other apps, such as BG Stats or BoardGameGeek, is not part of this. Neither is copying a whole Installation, which is what a backup is for (ADR 0020).

- **Export and Import are for Admins only.** They share a page, "Export and import", in the account menu, which only Admins see and reach. Each has an admin-only Permission of its own, like recalculating every Play of a Game, so API Keys can do neither (ADR 0017). An Export is mostly for moving Plays to another Installation, and JSON is not for reading.
- **An Export is one JSON file** of the whole shared record, downloaded as `board-keeper-YYYY-MM-DD.json`. It holds the Games (their name, their Game Module, and a Generic Game's recording defaults), every Player (their name only) and every Play: everything entered for it, who recorded it and when, and its Results. Players and Games without Plays are in it too. It holds nothing about Accounts and no statistics (ADR 0020).
- The format allows a file that holds only some Plays, with just the Players and Games they need, so an Export can be narrowed later without changing the format.
- **Every version of the format stays importable** (ADR 0022). The file says which version of the format it uses, and which Board Keeper wrote it and when. A file of a newer format than this Installation knows is refused, with the advice to update Board Keeper first. The API's description describes the current format, and the tests keep a sample file of each older one. A Game Module's keys never change once it is released, because Exports hold them.
- **An Import keeps each Play as it was** at the source: its UUID, its Results (ADR 0021), and who recorded it and when, the recorder mapped to a Player here like everyone else in the file. A Play that is already here is skipped, never overwritten, so importing the same file twice changes nothing.
- **Before anything is written, the Admin reviews the Import.** A Game with a module of its own is matched by its module. A Generic Game is matched by its name, also to a module's Game of that name, as the catalogue does when a module arrives; its Plays keep the Generic Module and their totals. Each Player in the file is suggested to match the Player in their place in a Play that is here already, as when the same file is imported again, else the Player of the same name, else to become a new Player. The Admin can change each suggestion. A new Player needs a name that is free here, and the step asks for another where it isn't. Each Player here can be matched by at most one Player in the file, because merging duplicate Players is a feature of its own (see Later). The same step checks every Play with this Installation's Game Modules, lists those that would be refused, including all Plays of a Game whose module this Installation doesn't have, and says how much the Import adds, such as "43 Plays, 2 new Players, 1 new Game".
- **Confirming writes everything else at once**, or nothing if something fails part-way. The server keeps nothing between the review and confirming: the browser sends the file again with the Admin's choices, and everything is checked again. An Import can't be undone, so the page advises making a backup first.
- It is delivered in two slices: Export, then Import. Each adds a line to the changelog, and the self-hosting guide gets a section on moving Plays to another Installation. Its Backups section names `pg_dump` as the backup, and an Export as a second line of defence for the Plays: after an Import into an empty Installation, only the Accounts have to be set up again.

## Settled for end-to-end tests

Playwright tests that drive Board Keeper in a real browser, against the real backend and database. The Vitest tests check pages with the API mocked, and the backend's tests call the API without a browser, so neither shows that the two halves work together.

- **1.0.0 waits for them.** Its release was stopped before anything was published. When it is made, after the end-to-end tests, the Unreleased lines join its changelog section, the Changed and Fixed lines about a version nobody had are dropped, and the section gets the new date: that is the "Release 1.0.0" commit.
- **They cover the main journeys**, each across the frontend and the backend, and what only a real browser can do. Details of single pages stay in the Vitest tests. The journeys:
  1. signing in and out with a password;
  2. an Invite: an Admin creates it, and a new Account registers through it, claims its Player and signs in;
  3. a Reset Link: an Admin creates it, and its owner chooses a new password and signs in with it;
  4. a competitive Play of a Generic Game, then in the play history and in the Game's statistics;
  5. a Play of 7 Wonders with Leaders, Roma as a Player Setup, and a tie the recorder decides;
  6. a Cooperative Play of Spirit Island, with its group score worked out;
  7. the Players of a Play put in another order, which the Score Pad's columns and the saved Play follow;
  8. editing and deleting a Play, and a Member not offered either on someone else's Play;
  9. a Passkey added in Settings, then used to sign in;
  10. a Play recorded without a connection, which waits on the device and is sent once the connection is back;
  11. an Export downloaded, and an Import of a sample Export: the review, matching its Players, confirming;
  12. switching to Hungarian, which survives a reload;
  13. the first Admin created through the Setup Link;
  14. the play history with 120 Plays: the newest 50, and "Show more" for the rest, each once and in order.
- **They run against the container image** that CI is about to publish, with PostgreSQL, through `deploy/compose.yaml` (ADR 0024): under a project name of their own, on port 8180, with `BOARD_KEEPER_IMAGE` naming the image (`board-keeper:local` unless set). Playwright's global setup starts it, and its global teardown removes it with its data.
- **A setup project starts every run.** It creates the first Admin through the Setup Link in the log (journey 13), then, through the screens, a Member who registered through an Invite, the Players Anna, Bence and Csilla, and a Generic Game, and saves how both Accounts are signed in. PostgreSQL then copies the database, and before every test the copy is put back, while the application keeps running: its connection pool replaces the connections that were cut. A trial showed it copes, so the fallbacks that were considered, emptying the tables and copying their rows back, or restarting the application after each reset, aren't needed.
- **Tests make their data through the screens**, like a person, except a lot of it at once, such as the 120 Plays of journey 14. Those are made through the API as the Admin, with request bodies typed from `api/openapi.json`.
- **Tests find elements by test id** (ADR 0023), and check text, in English, only where the text is what they test. axe checks the main page of each journey and fails on problems it calls serious or critical. The Vitest tests move to test ids in a pull request of their own, after the end-to-end tests. An ESLint rule then forbids the role, text and label queries in both.
- **They are a package of their own, `e2e/`**, beside `frontend/` and `backend/`, because they test the whole application.
- **Chromium only, at desktop size and as a Pixel 7.** Passkeys use Chromium's virtual authenticator. On the phone, the Players of journey 7 are dragged with a finger, through Chromium's DevTools protocol.
- **One worker, against one Installation.** An Installation per worker waits until a run is too slow.
- **No retries locally, one on CI.** A test that passes only when run again is a bug to fix.
- **CI runs them on every push.** It builds the image without publishing it, runs the tests against it, and publishes the same image only if they pass. When tests fail, it keeps Playwright's report with its traces.
- **The pull request also updates** `docs/development.md` (building the image, running the tests, reading a trace), `CLAUDE.md` (the command, and a test id on everything a test touches) and the README's Tests row. Tests stay out of the changelog.

## Details to settle when a milestone starts

- **M3 and M6:** each game's exact scoring and tie-break rules, taken from its official rulebook. Online summaries disagree, for example on the order of Calico's tie-breakers and on Spirit Island's score formula.
