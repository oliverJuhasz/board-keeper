# Developing Board Keeper

## What you need

- **JDK 25**
- **Node.js 24** (22.12 or later also works)
- **Docker**, running. The backend build starts a throwaway PostgreSQL to generate the jOOQ code ([ADR 0007](adr/0007-flyway-and-jooq-codegen-from-real-postgres.md)), and the tests use one too.

You don't need Maven. `./mvnw` downloads the right version itself; on Windows, use `mvnw.cmd`.

## Layout

```
api/openapi.json     the description of the API: written by the backend build, read by the frontend
backend/
  game-api/          the contract between the core and Game Modules
  games/             the Game Modules, one folder each
    generic/         the Generic Module, for Games without a module of their own
    calico/          the Game Module for Calico
    hadara/          the Game Module for Hadara
    harmonies/       the Game Module for Harmonies
    last-bastion/    the Game Module for Last Bastion, a cooperative game that scores only a victory
    seven-wonders/   the Game Module for 7 Wonders, with Leaders and Cities
    seven-wonders-duel/  the Game Module for 7 Wonders Duel, with Pantheon and Agora
    spirit-island/   the Game Module for Spirit Island, a cooperative game
    wyrmspan/        the Game Module for Wyrmspan
  app/               the Spring Boot application
frontend/            the React app
deploy/              an example Docker Compose setup
Dockerfile           packages the built application as a container image
CHANGELOG.md         what changed in each release
```

## Everyday commands

### Run it while developing

Use two terminals. First the backend:

```bash
cd backend
```

```bash
./mvnw spring-boot:test-run
```

This starts the backend on http://localhost:8080 with a throwaway PostgreSQL in Docker, so there is nothing to configure. The database is empty on every start, apart from one Admin to sign in with: username `admin`, password `correct horse battery staple`. That Account exists only in development; `DevelopmentAccount`, in the test sources, creates it. To try the real first-time setup, add `-Dboardkeeper.development.account=false` and open the Setup Link that the backend then writes to its log.

Then the frontend:

```bash
cd frontend
```

```bash
npm install
```

```bash
npm run dev
```

This serves the frontend on http://localhost:5173 and reloads it as you edit. Calls to `/api` are passed on to the backend. Swagger UI, for trying the API by hand, is at http://localhost:8080/swagger-ui.html.

### Check your work

| Where | Command | What it does |
|---|---|---|
| `backend/` | `./mvnw verify` | Generates the jOOQ code, compiles, and runs every test |
| `frontend/` | `npm test` | Runs the tests once (`npm run test:watch` reruns them as you edit) |
| `frontend/` | `npm run lint` | Looks for likely mistakes with ESLint |
| `frontend/` | `npm run typecheck` | Checks the TypeScript types |
| `frontend/` | `npm run format` | Reformats the code with Prettier |

### Build the whole application

The frontend is built first, because the backend build copies it into the jar.

```bash
cd frontend && npm ci && npm run build
```

```bash
cd backend && ./mvnw verify
```

```bash
docker build -t board-keeper:local .
```

To run that image, copy `deploy/.env.example` to `deploy/.env`, set `BOARD_KEEPER_IMAGE=board-keeper:local` in it, and run `docker compose up -d` in `deploy/`.

The image cannot be built from source inside `docker build`, because generating the jOOQ code needs Docker itself. That is why the Dockerfile packages a jar that already exists.

## How the parts fit together

### Two kinds of module in the backend

**Maven modules** separate the core from the Game Modules. `app` compiles against `game-api` only. Each Game Module is its own Maven module that also depends on `game-api` only, and `app` includes it at `runtime` scope. So the compiler stops the core from reaching into a Game Module, and a Game Module from reaching into the core.

**Spring Modulith modules** divide the core itself. Every package directly under `io.github.oliverjuhasz.boardkeeper` in `app` is one, such as `games`. Classes in a module's own package are its public interface. Classes in its sub-packages, such as `games.internal`, are hidden from other modules. `ModularityTests` fails the build when a module breaks this, and writes diagrams of the modules to `backend/app/target/spring-modulith-docs`.

### Each module has its own tables

The jOOQ classes for every table are generated into one shared package, so any module could, technically, query any table. `TableOwnershipTests` stops that: it lists which tables belong to which Spring Modulith module, and fails the build when a module touches a table that isn't its own. A module that needs another module's data asks that module's service, as `accounts` asks `Players` for a Player's name.

### How Game Modules are found

Game Modules are plain Java, with no Spring. The core finds them with Java's `ServiceLoader`: each module's jar names its class in `META-INF/services/io.github.oliverjuhasz.boardkeeper.gameapi.GameModule`. At startup, `GameModules` and `GameModuleTexts` check them all. The application refuses to start if two modules share an id or a key, or if one has no English text for something its score pad names.

Every module but the Generic Module brings a Game of its own. `Games.addGamesOfModules` runs at startup and puts that Game in the catalogue if it isn't there. Such a Game is shown under the name in the module's texts, and can be neither edited nor removed. If someone had already added a Game by hand under that name, it is handed to the module instead: its earlier Plays stay as the Generic Module recorded them, because every Play remembers the module that scored it.

### How a Play is recorded

1. The frontend sends `POST /api/plays` with the Game, the date, the Players, and what was entered about how it went. That last part has the same shape for every Game Module: a Recording Mode, the Setup as a value for each Variant, the scores of each Player by Score Category, and for a Cooperative Play the group's result.
2. `Plays.record`, in the `plays` module, checks what the core knows about: that the Game and the Players exist, that nobody is entered twice, and that the date is not in the future.
3. It hands the entry to the Game's Game Module: `GameModule.score(PlayEntry)` answers with an `Outcome`, or throws an `InvalidPlayException` with a reason. The core passes that on as the Problem `plays.invalidEntry`, with the reason as the name of a text: one of the module's own, such as `generic.invalid.winnerMissing`, or one of the frontend's for the reasons that are the same for every Game, such as `plays.invalid.tooFewPlayers`.
4. The Play is stored with both what was entered and what the module calculated ([ADR 0010](adr/0010-stored-results-recalculated-on-request.md)). The parts whose names belong to the Game Module, the Setup and the scores by Score Category, are kept in `jsonb` columns; `JsonColumns` converts them to and from Java maps.

`Outcome` is a `sealed` interface with two records, `Competitive` and `Cooperative`. Sealed means the compiler knows these are the only two kinds, so the `switch` in `Plays.record` must handle both and needs no `default`.

`PlayRepository` reads a Play together with its Results in one query, using jOOQ's `multiset`.

**Working out a Play before it is recorded.** `POST /api/plays/preview` runs the same scoring and stores nothing. The form calls it while it is filled in, to show the totals and Placings, and to learn whether first place is a tie that the scores cannot decide. The answer names the Players of such a tie in `tieForRecorder`; only then does the form ask who won. So the rules for totals and ties live in the Game Module alone, and the frontend repeats none of them.

**Recalculating a Play.** A Play's Results stay as its Game Module worked them out when it was recorded, until someone asks for a Recalculation ([ADR 0010](adr/0010-stored-results-recalculated-on-request.md)). `POST /api/plays/{id}/recalculation` builds the `PlayEntry` again from what is stored, scores it with the module the Play remembers, and replaces the Results only if they came out differently; the answer says whether they did (`changed`). It may be asked by whoever may change the Play. `POST /api/plays/recalculation?gameId=…` does the same for every Play of a Game, and needs `RECALCULATE_PLAYS`. A Play the module refuses now keeps its Results: for one Play the answer is the module's Problem, and for a whole Game the refused Plays are listed with their reasons, and the rest are recalculated anyway. A Play whose Results changed publishes `PlayChanged`, so the statistics catch up as they do after an edit.

### How the play history is paged

`GET /api/plays` answers with one page of Plays and, if there are more, a `next`. Asking again with `after=<that next>` gives the following page. `next` names the last Play of the page (its day and its id), and the next page starts right after that Play. `PlayRepository.findPage` does this with jOOQ's `seek`, which becomes `where (played_on, id) < (?, ?)` in SQL.

The other way to page, "skip the first 50 rows" (`offset`), gets slower the further you go, and shows a Play twice or skips one when Plays are added or deleted between two requests.

In the frontend, `PlaysPage.tsx` uses TanStack Query's `useInfiniteQuery`, which keeps the pages fetched so far and fetches the next one on "Show more". The filters are kept in the page's address (`/plays?game=…&mine=1`) with React Router's `useSearchParams`, so a filtered list can be bookmarked and survives a reload.

### How statistics keep up with the Plays

The `stats` module works out statistics from a copy of the Plays in tables of its own, `stats_play` and `stats_result`, and never reads the `plays` module's tables (ADR 0011). It keeps the copy up to date from events:

1. `Plays.record`, `change` and `delete` publish `PlayRecorded`, `PlayChanged` and `PlayDeleted` with Spring's `ApplicationEventPublisher`. An event carries only the Play's id.
2. Spring Modulith stores each event in the `event_publication` table, in the same transaction as the Play. If that transaction is rolled back, the event goes with it.
3. Once the transaction is committed, `StatsUpdater`'s methods marked `@ApplicationModuleListener` run, on a thread of their own and in a new transaction. Each asks `Plays.find` for the Play as it is now and copies it, or removes the copy when the Play is gone. Then Spring Modulith deletes the event.
4. An event left behind, because the application stopped before its listener finished or the listener failed, is handed to the listener again at the next start (`spring.modulith.events.republish-outstanding-events-on-restart`).

So a listener can see an event twice, and two listeners can run at once. Copying the Play as it is, rather than applying what the event says changed, gives the same result however often it runs; a PostgreSQL advisory lock stops two listeners for the same Play from overtaking each other. The statistics are a moment behind a Play that was just saved.

**Rebuilding the copy.** When `stats_play` is empty and Plays exist, `StatsStartup` fills it from every Play at startup. That happens the first time the application starts with the module, and whenever the copy is emptied on purpose: to change the shape of the statistics tables, add a migration that changes them and empties `stats_play` (its results go with it).

**Testing.** Because the listeners run on other threads, a test that records a Play and then reads statistics waits for them with Awaitility, which retries an assertion until it holds: see `StatsApiTests`.

**The pages.** `GET /api/stats/games/{id}` and `GET /api/stats/players/{id}` feed the pages at `/games/<id>` and `/players/<id>` in `frontend/src/features/stats/`. The tables they share are in `StatsTables.tsx`: a Game's page has a row for each Player, a Player's page a row for each Game. On a phone too narrow for every column the table scrolls sideways, with the first column held in place.

### Changing the database

1. Add a file to `backend/app/src/main/resources/db/migration`, named `V<next number>__<what_it_does>.sql`, with two underscores after the number. Never edit a migration that has been applied to a real database.
2. Build. The build applies every migration to a throwaway PostgreSQL and regenerates the jOOQ classes into `backend/app/target/generated-sources/jooq`. In an IDE, reload the Maven project or run "Generate Sources".
3. Use the regenerated classes, starting from `Tables`.
4. For a new table, add it to `TableOwnershipTests` under the module that owns it.

### Changing the API

`api/openapi.json` describes the API, and the frontend's TypeScript types are generated from it. A backend test fails when that file no longer matches the real API, so the two cannot drift apart unnoticed.

1. Change the controller.
2. In `backend/`, run `./mvnw verify -Dboardkeeper.openapi.update=true` to rewrite `api/openapi.json`.
3. Commit the file along with your change.
4. In `frontend/`, `npm run generate:api` regenerates `src/api/schema.d.ts`. It also runs by itself before `npm run dev` and `npm run build`. TypeScript then reports every place in the frontend that has to follow the change.

### Signing in, Permissions and errors

**Sessions.** Signing in creates a session, stored in PostgreSQL by Spring Session ([ADR 0013](adr/0013-sessions-in-postgresql.md)), and the browser gets a `SESSION` cookie. The session holds only the Account's id. On every API request, `AccountRefreshFilter` loads the Account and its Permissions afresh, so a change of Roles or a disabled Account takes effect at once. `SecurityConfiguration` in the `accounts` module holds the rules: which addresses are open, and that everything else under `/api` needs a signed-in Account.

**Forged requests (CSRF).** The server hands out a token in the `XSRF-TOKEN` cookie, and the frontend sends it back in the `X-XSRF-TOKEN` header on every request that changes something; `frontend/src/api/client.ts` does this for all API calls. Another website can make a browser send Board Keeper's cookies, but cannot read them to fill in the header.

**Who is acting.** A controller method gets the signed-in Account by declaring a parameter:

```java
@PutMapping("/language")
void setLanguage(@AuthenticationPrincipal CurrentAccount account, @RequestBody LanguageRequest body) { ... }
```

**Guarding a method with a Permission.** Put `@RequiresPermission` on it. Anyone without the Permission gets "403 Forbidden":

```java
@PostMapping
@RequiresPermission(Permission.CREATE_PLAYERS)
PlayerResponse create(@Valid @RequestBody CreatePlayerRequest body) { ... }
```

To add a Permission, add it to the `Permission` enum and to `frontend/src/features/roles/permissions.ts`, regenerate `api/openapi.json`, and add its name to the three translation files. The Admin Role gets it automatically. In the frontend, `useCan()` tells whether the signed-in Account holds a Permission, for showing or hiding what needs it.

**Permissions that depend on the item.** Whether an Account may change a Play depends on the Play: there is a Permission for Plays you recorded, one for Plays you took part in, and one for any Play. An annotation on the method cannot decide that, because it runs before the Play is known. So the service decides: `Plays.mayChange(play, account)` holds the rule, and `Plays.change` and `Plays.delete` throw `Problem.forbidden` when it says no. The API tells the frontend the outcome for each Play as `canChange`, so the rule lives in one place and the frontend does not repeat it.

**Admin-only Permissions.** Managing Roles, Accounts and Invites belongs to the Admin Role alone ([ADR 0015](adr/0015-admin-powers-are-admin-only.md)), and so does recalculating every Play of a Game. In `Permission`, these are marked `adminOnly`. They are checked like any other Permission, with `@RequiresPermission`, but no other Role can hold them: `RoleAdministration` refuses, and `RoleRepository` ignores such a row even if the database has one.

**One-time links.** An Invite or a Reset Link is a link with a random token in it. The token is shown once, when the Invite is created; the database keeps only a hash of it (`LinkTokens`), so reading the database does not give anyone a usable link. `Invites` and `RegistrationController` show the pattern.

**Reporting an error.** Throw a `Problem` with a code. The backend never writes the message; the frontend has a text for every code, under `problems` in the translation files:

```java
throw Problem.conflict("players.nameTaken");
```

`Problem.conflict` answers "409 Conflict", `Problem.notFound` "404 Not Found", `Problem.forbidden` "403 Forbidden", and `Problem.unprocessable` "422 Unprocessable Content", for a request that is well-formed but asks for something that breaks a rule.

Rules on a request's fields are annotations such as `@Size(min = 16)` on the request record, checked because the parameter is marked `@Valid`. The frontend's forms check the same rules first, with Zod, so the person sees the message next to the field without a round trip.

**Tests.** `TestBrowser` calls the API as a browser would: it keeps cookies between requests and sends the CSRF token. `TestAccounts` creates Accounts, and `TestDatabase.empty()` clears the database between tests that make several requests. A test that only needs "someone is signed in" can use `@WithMockUser` instead.

### Passkeys

A passkey signs in with a phone, computer or security key instead of the password; every Account keeps its password too. Spring Security's WebAuthn support (`spring-security-webauthn`, with the WebAuthn4J library underneath) does the cryptography. The `accounts` module uses it in endpoints of its own, like signing in with a password, rather than Spring Security's ready-made filters and pages: `Passkeys` holds the logic and `PasskeyController` the endpoints.

Adding a passkey and signing in with one each take two requests. The first asks the server for options with a random challenge, which wait in the session. The browser converts them (`PublicKeyCredential.parseCreationOptionsFromJSON` or `parseRequestOptionsFromJSON`), asks the device, and sends its answer back as JSON (`toJSON()`). The server checks that answer against its own challenge. Signing in with a passkey then starts the same session as a password does. Spring Security keeps the passkeys in two tables of its own, `user_entities` and `user_credentials` (V13), which jOOQ leaves out like Spring Session's.

A passkey belongs to one web address, so Board Keeper offers passkeys only when `boardkeeper.url` (the `BOARDKEEPER_URL` environment variable) says where it is reached. `GET /api/session` answers whether it does (`passkeys`). While developing, the address is Vite's, `http://localhost:5173` (set in `backend/app/src/test/resources/config/application.properties`), so passkeys work through `npm run dev`, not on Spring Boot's own port. Browsers allow plain http only on `localhost`.

**Tests.** `TestPhone` is a pretend phone that makes and uses passkeys with real keys, from WebAuthn4J's emulated authenticator; `PasskeyTests` shows it at work. In the frontend, the tests stand in for the browser's `PublicKeyCredential` and `navigator.credentials` (`passkeys.test.tsx`). To try passkeys in Chromium without a phone, its developer tools can add a virtual authenticator (More tools, WebAuthn).

### API Keys

An API Key lets a program, such as a script or an AI agent, use the API as one Account, without signing in ([ADR 0017](adr/0017-api-keys-instead-of-oauth.md)). Its owner creates it on the settings page, which shows it once; the database keeps only its hash, as for a one-time link. Using API Keys needs the Permission `USE_API_KEYS`, which the Admin Role has and no other Role starts with.

A program sends the API Key in a header, `Authorization: Bearer bk_…`. `ApiKeyFilter` reads it, `ApiKeys.check` finds its Account, and the request then acts as that Account, with the Permissions it holds at that moment minus the Admin-only ones. Controllers get it as the usual `@AuthenticationPrincipal CurrentAccount`, so nothing else has to know about API Keys. What sets such a request apart:

- **No session.** The filter puts the Account into the request's security context and nowhere else, so the request creates no session. If it also carries a session cookie, the API Key wins, and an API Key that isn't accepted is refused without falling back to the session.
- **No CSRF token.** The token guards against a browser sending cookies on another website's behalf. A browser never adds an `Authorization` header by itself, so `SecurityConfiguration` leaves requests with an API Key out of the CSRF check. Only a header that starts with `Bearer bk_` counts; any other `Authorization` header, such as one from a reverse proxy, is left alone.
- **No signing in.** A rule in `SecurityConfiguration` refuses API Keys at every address about signing in: signing in and out, registering, Reset Links, and changing the password, passkeys or API Keys. `ApiKeyAuthentication`, the kind of authentication the filter creates, is how the rule tells such a request apart.

Creating an API Key is confirmed with the password or a passkey, because an API Key outlives the session it was made in. The passkey's options come from `POST /api/account/api-keys/passkey-options` and wait in the session apart from those for signing in (`Passkeys.startConfirming` and `confirm`). Disabling an Account deletes its API Keys; changing its password doesn't.

To try the API with an API Key, create one on the settings page and send it along:

```bash
curl -H "Authorization: Bearer bk_..." http://localhost:8080/api/session
```

```bash
curl -X POST -H "Authorization: Bearer bk_..." -H "Content-Type: application/json" -d '{"name": "Zsófi"}' http://localhost:8080/api/players
```

`GET /api/session` tells a program which Account it acts as and what it may do. In Swagger UI, the **Authorize** button takes an API Key and sends it with every "Try it out"; `ApiDocumentationConfiguration` declares it. The API isn't versioned yet: it changes along with the frontend, so a program may need changing after an update.

**Tests.** `ApiKeyTests` sends requests as a script would: only the header, without cookies or a CSRF token.

### Forms in the frontend

A form is described once with Zod, which gives both the checks and the TypeScript type, and run by React Hook Form. `frontend/src/features/session/SignInPage.tsx` is the example to copy. `TextField` draws a labelled input with its hint and error; `FormProblem` shows why the server refused a submission.

`frontend/src/features/plays/` is the example of a bigger form. It is drawn from the score pad of the Game's Game Module: the Variants become choices and the Score Categories the rows of a pad, and what each is called comes from the module's texts. Its rules and what it sends are in `playForm.ts`, apart from the page, so they can be tested without drawing anything. `usePlayPreview.ts` asks the server how the Play would end, a moment after the recorder stops typing. The form itself, `PlayEntryForm.tsx`, is used by two pages: one records a new Play and one edits an existing Play. Each page says what the form starts with and where the result is sent. It uses three more parts of React Hook Form and Zod:

- `useFieldArray` for the list of Players, where rows are added, removed, and put in another order with its `move`. The order is kept with the Play (`entry_order`), and the pad's columns follow it;
- `useWatch` to redraw when a field changes that others depend on, such as the Recording Mode;
- Zod's `superRefine` for rules that involve several fields, such as "a competitive Play needs two Players".

### The installable app

Board Keeper can be installed as an app, on a phone's home screen for example. `vite-plugin-pwa` (configured in `frontend/vite.config.ts`) adds two files to the build:

- `manifest.webmanifest`, the app's name, colours and icons, which a browser reads to offer "Install" or "Add to Home Screen". Spring Boot serves it as `application/manifest+json` (`FrontendResourceConfiguration`).
- `sw.js`, a service worker: a script the browser runs apart from the page. It keeps every file of the build on the device and serves them from there, so the app opens without waiting for the network, and opens at all without one (it then says it can't reach Board Keeper). Calls to `/api` always go to the server. Workbox, Google's service worker library, does the work.

**Updates.** A browser looks for a new `sw.js` whenever a page of the app is opened, and an open app asks every hour too. A new version is fetched in the background, but the app goes on with the old one until the person presses Reload in the notice (`frontend/src/routes/UpdateNotice.tsx`), so nothing they are doing is lost. `index.html` and `sw.js` are served with "check with the server on every use"; the files under `assets/` carry a hash of their content in their names and are cached for good.

**Icons.** `frontend/public/` holds the icons in the favicon's design: `icon-192.png` and `icon-512.png` with rounded corners; `icon-maskable-512.png`, which fills the square and keeps the pips in the middle circle of 80%, for Android to cut into its own shape; and `apple-touch-icon.png` (180 by 180, no transparency) for an iPhone's home screen. To change the design, draw all four again at these sizes.

**Trying it.** The service worker is part of the production build only: `npm run dev` has none, so changes show at once. Build the frontend, then the backend, and open Spring Boot's own port (8080); a browser allows service workers on `localhost` and on https addresses. In Chromium's developer tools, the Application tab shows the manifest, the service worker and the files it keeps, and can go offline or skip waiting.

### Recording without a connection

New Plays can be recorded without a connection; existing Plays can't be changed then, so two devices never change the same Play differently.

- **The Play's id comes from the device.** The form gives each new Play a UUID of version 7 (`frontend/src/lib/uuid.ts`), which starts with the time like the database's own, and sends it with the Play. The server records a Play once per id: the same id from the same Account gives back the Play already recorded (`Plays.record`), so sending again after a lost answer does no harm.
- **What the form needs is kept on the device.** TanStack Query's persistence (`frontend/src/api/persistence.ts`) saves part of its cache in localStorage and reads it back when the app starts: who is signed in, the Games, the Players, the Game Modules with their texts, and the recent Plays. Who is signed in is only kept while someone is: the cache is saved at most once a second, and a kept "signed out" would send a page loaded right after signing in back to the sign-in page. What one version of the app kept is thrown away by any other: the cache's `buster` is the commit the frontend was built from (`__APP_VERSION__`, which `vite.config.ts` writes into the code). A new version's answers can have another shape, and it can bring other Game Modules, which the app otherwise loads only once; so the first start after an update loads everything again. Without a connection, a query with nothing kept doesn't fail: it pauses, and `QueryState` says so. TanStack Query learns about the connection from the browser's online and offline events; `queryClient.ts` also asks the browser when the app starts.
- **A Play that can't be sent waits on the device** (`waitingPlays.ts`), when the device is offline or the server doesn't answer. The form then neither shows totals nor asks about a tie (`onUnreachable` in `PlayEntryForm`). The start page lists what waits.
- **Sending** (`useSendWaitingPlays`, used in `AppLayout`) happens when the app starts, when the device comes back online, and every minute while something waits, for the Account that recorded it only. Before sending, it asks the server how the Play ends: a tie only the recorder can decide, or a refusal, leaves the Play waiting for them, and opening it from the start page fills in the form again (`/plays/new?waiting=<id>`).

To try it, use the production build on Spring Boot's own port, as for the installable app, and the developer tools' "Offline" switch (Network or Application tab).

### Adding a page

Give the page an address in `frontend/src/router.tsx`, with `lazy` like the pages already there, not with `element`. The build then puts the page's code into a file of its own, which the browser loads the first time the page is shown. `npm run build` lists the files and their sizes.

In tests, `renderApp` loads every page before a test file's first test, so a test doesn't spend its waiting time on compiling a page. It also gives each test a router of its own, made from fresh route objects (`appRoutes()`), because React Router keeps what it loaded for a page on the page's route object. After a test follows a link, the page it came from stays on screen until the next page's code has loaded, so the test should wait for something only the next page shows. The start page and the form for a Play, for example, have the same heading.

### Building blocks for pages

New pages are put together from these, in `frontend/src/components/`. `frontend/src/features/roles/RolesPage.tsx` uses most of them and is a good page to copy.

| Component | What it is for |
|---|---|
| `PageHeader` | A page's title, a line about it, and an optional button |
| `QueryState` | Placeholder rows while data loads, a message if loading failed |
| `ActionsMenu` | The "…" menu at the end of a table row |
| `ConfirmDialog` | "Are you sure?" before something that can't be undone |
| `LinkDialog` | Creating a one-time link and showing it to copy |
| `LinkButton` | A link to another page that looks like a button |
| `QuietLink` | A link in text or a table, such as a name that leads to its statistics, with a faint underline |
| `SortableList`, `SortableItem` | A list whose items can be put in another order, by dragging a handle or from the keyboard, as the Players of a Play are. It uses dnd-kit (`@dnd-kit/react`); `sortableListsCanBeUsed()` in `src/test/sortable.ts` lets a test move its items from the keyboard |
| `TextField`, `TextAreaField`, `CheckboxField`, `RadioField`, `SelectField` | A labelled input, text box, checkbox, set of a few choices, or drop-down of many, with hint and error |
| `FormProblem` | Why the server refused a submission |
| `PageMessage` | A page that only has something to say |

The components in `components/ui/` are shadcn/ui's, added with its command line tool. Leave those as generated where possible, and build on top of them.

**Dialogs** are opened from the page's state and taken off the page when they close, as in `PlayersPage`. Closing one should give focus back to whatever opened it, so that someone using the keyboard carries on from there. Base UI, which the dialogs are built on, does this by itself, except when a field in the dialog has `autoFocus`. Base UI then notes that field as what had focus before, and it is gone once the dialog has closed. Such a dialog takes a `finalFocus` with the button that opens it, as `PlayerNameDialog` does. A dialog opened from an `ActionsMenu` needs none: focus goes back to the menu's button. Keep `autoFocus` rather than switching to Base UI's `initialFocus`: Base UI moves focus a moment later, and a phone may then not bring up its keyboard.

### Texts and translations

Every text the user sees comes from a translation file, in English, Dutch and Hungarian. There are two kinds.

**The frontend's own texts** are in `frontend/src/i18n/locales/en.json`, `nl.json` and `hu.json`. To add one:

1. Add it to all three files under the same name, such as `games.empty`. A test fails when the files don't have the same names.
2. Use it in a component:

```tsx
const { t } = useTranslation()
return <p>{t("games.empty")}</p>
```

TypeScript knows the names from `en.json`, so a misspelt name does not compile.

**A Game Module's texts** are in the module itself, in `i18n/<key>_en.properties`, `_nl` and `_hu`, saved as UTF-8. Every name starts with the module's key, such as `calico.name`; "Adding a Game Module" below lists the names a module needs. English is required; a text missing in Dutch or Hungarian is shown in English, and the backend logs a warning at startup. The frontend loads these texts from `/api/game-modules/texts/{language}` through the `useGameModuleTexts` hook, in `frontend/src/features/games/`.

**Which language is shown:** the one chosen with the switcher, which the browser remembers; otherwise the browser's own language; otherwise English. `frontend/src/i18n/index.ts` sets this up. The backend's list of languages is the `Language` enum.

Names that people type in, such as a Player's or a Game's, are never translated.

### Adding a user-interface component

shadcn/ui components are not a library you import. Their source is copied into the project, where you can read and change it:

```bash
npx shadcn@latest add dialog
```

This writes `frontend/src/components/ui/dialog.tsx`. Run `npm run format` afterwards.

### Adding a Game Module

`backend/games/calico` is the example to copy; `backend/games/harmonies` shows a Variant that is a choice, and rows that a Variant swaps; `backend/games/wyrmspan` shows a Game played alone against an automated opponent; `backend/games/hadara` shows a choice with many options, which the form offers as a drop-down instead of radio buttons once there are more than five; `backend/games/seven-wonders-duel` shows Expansions and a Game that can be won in more than one way; `backend/games/seven-wonders` shows a Player Setup and rows that can be negative; `backend/games/spirit-island` shows a cooperative Game whose module works out the group's score, and Variants that are numbers or depend on another choice; `backend/games/last-bastion` shows a cooperative Game that scores only a victory, and a choice whose default isn't its first option. A module has three parts: an identity, a description of its Game's score pad, and its texts.

1. Copy `backend/games/calico` to `backend/games/<key>` and rename the artifact to `game-<key>`.
2. Give the module a newly generated UUID. It is stored with every Play, so it never changes afterwards ([ADR 0012](adr/0012-game-module-uuid-and-key.md)).
3. Name the module's class in its `META-INF/services` file.
4. Describe the Game in `scorePad()`: how many can play, its Expansions and Variants, the rows of its score pad as Score Categories, its Tie-breakers, the ways it can be won, if there is more than one, and its Player Setup, if each Player chooses something. Take all of it from the Game's official rulebook, and say in the class's Javadoc where each rule comes from.
5. Extend `StandardGameModule` if the Game is scored the usual way: every Player adds up the rows of the pad and the highest total wins. The scoring then follows from the description, and there is no `score` to write. A Game that works differently implements `GameModule` itself, as the Generic Module does. Its `score` must depend on the entry alone, because a Recalculation runs it again over the stored entry.
6. Add `i18n/<key>_en.properties`, and the same for `nl` and `hu`. English needs a text for everything the score pad names, or the application refuses to start:

   | Text | For |
   |---|---|
   | `<key>.name` | the Game, as the catalogue lists it |
   | `<key>.expansion.<expansion>` | an Expansion |
   | `<key>.variant.<variant>` | a Variant; `<key>.variant.<variant>.hint` adds a line under it, and is optional |
   | `<key>.variant.<variant>.<option>` | each option of a Variant that is a choice; for a Variant that is a number, a name for one number, such as `.0` for "Base level", is optional |
   | `<key>.category.<category>` | a row of the score pad; a cooperative Game may name a row differently after a win and a loss with `.won` and `.lost` |
   | `<key>.victoryType.<victoryType>` | a way to win the Game |
   | `<key>.playerSetup.<choice>` | a choice of the Player Setup, such as a Wonder board |
   | `<key>.playerSetup.<choice>.<option>` | each option of such a choice |
   | `<key>.tie` | how the Game decides a tie, when the recorder is asked who won one |
   | `<key>.invalid.<reason>` | a reason of the module's own for refusing an entry |

7. Test the module with plain unit tests, as `CalicoGameModuleTests` does. A Game Module needs no Spring and no database. Use a scoring example from the rulebook as one of the tests.
8. Register it in three places: as a `<module>` and under `<dependencyManagement>` in `backend/pom.xml`, and as a `runtime` dependency in `backend/app/pom.xml`.

Nothing else changes. When the application next starts, the Game is in the catalogue, and the frontend draws its form from the description.

**Solo Plays.** Give the score pad a `Solo` if the Game can be played alone, and make its minimum one Player. `Solo.forScoreOnly()` records a total and nothing else: such a Play has no Placing and is neither won nor lost. `Solo.againstOpponent(row, tieWins)` adds a row for the opponent's score, which must be marked `notCounted()` and `inSoloPlaysOnly()`; the Player wins by beating it. Variants that only apply when playing alone, such as the Automa's level, are marked `inSoloPlaysOnly()` too, and the form shows them only for a Play of one.

**Expansions.** List the Expansions a Play can be played with. In a Play's Setup each is `"true"` or `"false"` under its key, next to the Variants, and the form offers them as checkboxes, before the Players. What an Expansion changes is declared on the pad, never checked in code of the module's own, so that the form can follow it while the Setup is chosen ([ADR 0019](adr/0019-expansions-declare-what-they-change.md)):

- A row that an Expansion adds is `onlyWhen("pantheon", Expansion.WITH)`, and a row it takes away is `onlyWhen("pantheon", Expansion.WITHOUT)`. `onlyWhen` names a Variant or an Expansion alike.
- An Expansion that lets more Players take part says how many: `new Expansion("skullport").allowingUpTo(6)`. The most Players a Play can have is then the highest of the pad's own maximum and those of the Expansions it is played with.
- A Player Setup option that comes with an Expansion is marked on its choice: `.onlyWith("leaders", List.of("roma", "abuSimbel"))`. The form offers it only once the Expansion is ticked, and keeps it, marked as a problem, if the Expansion is taken off afterwards. `StandardGameModule` and `CooperativeGameModule` refuse it in a Play without the Expansion (`needsExpansion`).

**Ways to win.** A Game that can be won in more than one way lists its Victory Types, the usual one first. `VictoryType.onPoints(key)` is decided by the score pad. `VictoryType.outright(key)` ends the Play before anyone counts points, such as a supremacy in 7 Wonders Duel: the form then takes the pad away and asks for the one winner, and the Play is recorded like one with only the winner. A Victory Type can depend on the Setup through `onlyWhen`, as political supremacy depends on Agora. A Play of such a Game always says how it was won, except a Solo Play, which has no Victory Type. A Game's statistics count its Plays by Victory Type.

**The default of a Variant.** A switch is off unless it says otherwise, a number starts at its lowest, and a choice at its first option. Where another option is the usual one, name it with `byDefault`, so that the options can stay in their natural order: `new Variant.Choice("difficulty", List.of("novice", "normal", "difficult", "heroic")).byDefault("normal")`.

**Variants that are numbers, or depend on another choice.** `new Variant.Range(key, min, max)` is a whole number, such as an adversary's level from 0 to 6; the form offers its numbers, under the names the texts give them. A Variant can depend on the Setup with `onlyWhen`, as Spirit Island's level of England is only chosen once England is the adversary: the form shows it then, and only then is it sent.

**Cooperative Games.** Make the score pad with `ScorePad.cooperative(...)` and extend `CooperativeGameModule`. The Players win or lose together; the pad's rows are the group's, entered once for the Play when it is recorded in points, and `groupScore` works out the group's score from them. The pad says which Recording Mode a Play starts with: Spirit Island's starts with only won or lost, because its rulebook calls scoring optional. A Game that scores only a victory, such as Last Bastion, marks its rows `onlyAfterVictory()`: the form shows them once "The group won" is chosen, and a defeat recorded in points has no rows and no score.

**Player Setup.** List what is chosen for each Player, such as their Wonder board and its side, as `PlayerSetupChoice`s. The form shows a drop-down for each in every Player's row, and a choice can be left open. Mark a choice `eachOptionOncePerPlay()` where each option exists once, as each Wonder board does: the form greys out what another Player has, and the module refuses a Play that gives two Players the same option. Name a choice so that it can't be mistaken for a row of the pad: 7 Wonders has a "Wonder" row, so its choice is the "Wonder board".

**Ties.** List as `tieBreakers` the Tie-breakers that can be worked out from what is entered. Set `recorderDecidesTies` when the Game decides what is left by something that is not on the pad; the form then asks the recorder who won, and shows `<key>.tie` as the explanation. Calico is such a Game: its first Tie-breaker is the number of cat tokens, and the pad only has the cats' points.

**Reasons for refusing an entry.** `StandardGameModule` refuses what is wrong in the same way for every Game, such as too few Players or a value a row doesn't allow. Those are the `Standard` reasons of `InvalidPlayException`, and the frontend has their texts, under `plays.invalid` in its translation files. A module only writes texts for reasons of its own.

### The licence

Board Keeper is under the AGPL, version 3 or later ([ADR 0016](adr/0016-agpl-licence.md)). Two parts of the build come from that.

**The links to the source code.** The AGPL asks whoever runs a changed copy for others to offer them its source, so the About page links to the repository and to the commit the running version was built from. The addresses are the `boardkeeper.source.*` settings, which `SourceCode` in the `about` module holds, so that a changed copy can point them at its own code. `GET /api/about` fills in the commit and the version. "The About page" below says where those come from.

**The licences of the libraries.** The libraries' licences ask for their notices to go along with every copy, and minifying the web app throws away the comments that held them. A small Vite plugin, `frontend/vite-plugins/thirdPartyLicenses.ts`, writes the licence of every package in the build into `third-party-licenses.txt`. It finds the packages the JavaScript imports by itself; a package that reaches the browser another way, such as through a stylesheet, is named in `vite.config.ts`, so add it there. The backend's libraries go into the jar, and the image, as their own unchanged jars, with whatever licence files they carry.

### The About page

`AboutPage.tsx`, in `frontend/src/features/about/`, shows which version is running. Everything it knows about that comes from `GET /api/about`.

**The version comes from git** ([ADR 0018](adr/0018-releases-are-git-tags.md)). While the backend is built, `git-commit-id-maven-plugin` writes what git knows into `git.properties`: the commit and its time, whether files were changed since, the tags on the commit, and the newest release before it with the number of commits in between. Spring Boot reads the file into a `GitProperties` bean, and `Build`, in the `about` module, decides from it: a commit with a tag such as `1.2.3` is that release, and anything else is a Snapshot. The plugin records nothing else, because by default it would also write who built the jar, on which machine, and their e-mail address, into a jar that gets published. A build from a copy without Git doesn't know its version, and the page says so.

While developing, the page shows a Snapshot, "with uncommitted changes" as soon as a file differs from the commit. To see it as a release, commit your work, tag the commit (`git tag 9.9.9`), start the backend again, and delete the tag afterwards (`git tag -d 9.9.9`). Don't push such a tag.

**The app on the device** knows the commit it was built from too: `__APP_VERSION__`, which `vite.config.ts` writes into the code. When it isn't the server's, the server was updated and the device still has the app as it was, so the page says so and offers to reload. `reloadIntoNewestVersion`, in `frontend/src/lib/appUpdate.ts`, asks the service worker for the new version and reloads the page once that has taken over. `npm run dev` and the backend each read the commit when they start, so while developing the line can also show after a commit, until both are started again.

## Releases

A release is a git tag such as `1.2.3` on a commit on `main` ([ADR 0018](adr/0018-releases-are-git-tags.md)). No file holds the version: the build reads it from git, and the `0-SNAPSHOT` in the poms never changes.

### What a version number says

A guideline, not a promise:

| Part | Goes up when |
|---|---|
| The first, as from 1.4.0 to 2.0.0 | Someone who runs Board Keeper, or a script that uses its API, has to do something when updating: a setting was renamed, or the API changed in a way that breaks scripts. |
| The second, as from 1.4.0 to 1.5.0 | There is something new, such as a feature or a Game Module. |
| The third, as from 1.4.0 to 1.4.1 | It only fixes things. |

### The changelog

`CHANGELOG.md` follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Every pull request that changes something a person who uses or runs Board Keeper would notice adds a line under `## [Unreleased]`, below one of the headings Added, Changed, Deprecated, Removed, Fixed and Security. Write it in English, in the terms of `CONTEXT.md`. Refactoring, tests and internal docs stay out.

### Making a release

1. On `main`, in `CHANGELOG.md`, rename `## [Unreleased]` to the version and the day, such as `## [1.2.3] - 2026-12-02`, and put a new, empty `## [Unreleased]` above it. At the bottom of the file, give the release a link, and point `[unreleased]` at what comes after it:

   ```markdown
   [unreleased]: https://github.com/oliverJuhasz/board-keeper/compare/1.2.3...HEAD
   [1.2.3]: https://github.com/oliverJuhasz/board-keeper/compare/1.2.2...1.2.3
   ```

   The first release has nothing to be compared with, so its link is `https://github.com/oliverJuhasz/board-keeper/releases/tag/1.0.0`.

2. Check what the release will say:

   ```bash
   .github/scripts/release-notes.sh 1.2.3
   ```

3. Commit that as "Release 1.2.3" and push it.
4. Tag that commit, and push the tag:

   ```bash
   git tag 1.2.3
   ```

   ```bash
   git push origin 1.2.3
   ```

CI then checks that the tag is on a commit on `main` and that the changelog has a section for it, and stops if not. Otherwise it builds and tests as always, makes sure the build found its version, publishes the image as `1.2.3` and `latest`, and creates a GitHub Release with the changelog's section as its text.

The image that was published as `sha-…` when the commit reached `main` was built before the tag was there, so it goes on calling itself a Snapshot. It holds the same code as the release.

If CI stopped because something was missing, take the tag away again, fix it, and tag anew:

```bash
git push --delete origin 1.2.3
```

```bash
git tag -d 1.2.3
```

## Continuous integration

`.github/workflows/ci.yml` runs on every push. It checks and tests the frontend, builds it, builds and tests the backend, and builds the container image. It publishes the image to GitHub Container Registry as `ghcr.io/oliverjuhasz/board-keeper`: from `main` tagged `sha-<commit>` and `snapshot`, and for a release tagged with its version and `latest`. A second job then creates the GitHub Release; it is apart so that only that job may write to the repository.

## Tooling notes

- **TypeScript is held at 5.9.** The tools that generate the API types (openapi-typescript) and lint TypeScript (typescript-eslint) do not support TypeScript 7 yet.
- **jOOQ code generation uses three Maven plugins** in `backend/app/pom.xml`: one starts PostgreSQL with Testcontainers, Flyway's applies the migrations, and jOOQ's generates the code. A single plugin that does all three exists (`testcontainers-jooq-codegen-maven-plugin`), but its last release was in April 2024 and it is built against Flyway 9 and Testcontainers 1.19.
