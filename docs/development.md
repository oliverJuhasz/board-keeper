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
  app/               the Spring Boot application
frontend/            the React app
deploy/              an example Docker Compose setup
Dockerfile           packages the built application as a container image
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
4. The Play is stored with both what was entered and what the module calculated ([ADR 0010](adr/0010-results-stored-recalculated-on-request.md)). The parts whose names belong to the Game Module, the Setup and the scores by Score Category, are kept in `jsonb` columns; `JsonColumns` converts them to and from Java maps.

`Outcome` is a `sealed` interface with two records, `Competitive` and `Cooperative`. Sealed means the compiler knows these are the only two kinds, so the `switch` in `Plays.record` must handle both and needs no `default`.

`PlayRepository` reads a Play together with its Results in one query, using jOOQ's `multiset`.

**Working out a Play before it is recorded.** `POST /api/plays/preview` runs the same scoring and stores nothing. The form calls it while it is filled in, to show the totals and Placings, and to learn whether first place is a tie that the scores cannot decide. The answer names the Players of such a tie in `tieForRecorder`; only then does the form ask who won. So the rules for totals and ties live in the Game Module alone, and the frontend repeats none of them.

### How the play history is paged

`GET /api/plays` answers with one page of Plays and, if there are more, a `next`. Asking again with `after=<that next>` gives the following page. `next` names the last Play of the page (its day and its id), and the next page starts right after that Play. `PlayRepository.findPage` does this with jOOQ's `seek`, which becomes `where (played_on, id) < (?, ?)` in SQL.

The other way to page, "skip the first 50 rows" (`offset`), gets slower the further you go, and shows a Play twice or skips one when Plays are added or deleted between two requests.

In the frontend, `PlaysPage.tsx` uses TanStack Query's `useInfiniteQuery`, which keeps the pages fetched so far and fetches the next one on "Show more". The filters are kept in the page's address (`/plays?game=…&mine=1`) with React Router's `useSearchParams`, so a filtered list can be bookmarked and survives a reload.

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

**Admin-only Permissions.** Managing Roles, Accounts and Invites belongs to the Admin Role alone ([ADR 0015](adr/0015-admin-powers-are-admin-only.md)). In `Permission`, these are marked `adminOnly`. They are checked like any other Permission, with `@RequiresPermission`, but no other Role can hold them: `RoleAdministration` refuses, and `RoleRepository` ignores such a row even if the database has one.

**One-time links.** An Invite or a Reset Link is a link with a random token in it. The token is shown once, when the Invite is created; the database keeps only a hash of it (`LinkTokens`), so reading the database does not give anyone a usable link. `Invites` and `RegistrationController` show the pattern.

**Reporting an error.** Throw a `Problem` with a code. The backend never writes the message; the frontend has a text for every code, under `problems` in the translation files:

```java
throw Problem.conflict("players.nameTaken");
```

`Problem.conflict` answers "409 Conflict", `Problem.notFound` "404 Not Found", `Problem.forbidden` "403 Forbidden", and `Problem.unprocessable` "422 Unprocessable Content", for a request that is well-formed but asks for something that breaks a rule.

Rules on a request's fields are annotations such as `@Size(min = 16)` on the request record, checked because the parameter is marked `@Valid`. The frontend's forms check the same rules first, with Zod, so the person sees the message next to the field without a round trip.

**Tests.** `TestBrowser` calls the API as a browser would: it keeps cookies between requests and sends the CSRF token. `TestAccounts` creates Accounts, and `TestDatabase.empty()` clears the database between tests that make several requests. A test that only needs "someone is signed in" can use `@WithMockUser` instead.

### Forms in the frontend

A form is described once with Zod, which gives both the checks and the TypeScript type, and run by React Hook Form. `frontend/src/features/session/SignInPage.tsx` is the example to copy. `TextField` draws a labelled input with its hint and error; `FormProblem` shows why the server refused a submission.

`frontend/src/features/plays/` is the example of a bigger form. It is drawn from the score pad of the Game's Game Module: the Variants become choices and the Score Categories the rows of a pad, and what each is called comes from the module's texts. Its rules and what it sends are in `playForm.ts`, apart from the page, so they can be tested without drawing anything. `usePlayPreview.ts` asks the server how the Play would end, a moment after the recorder stops typing. The form itself, `PlayEntryForm.tsx`, is used by two pages: one records a new Play and one edits an existing Play. Each page says what the form starts with and where the result is sent. It uses three more parts of React Hook Form and Zod:

- `useFieldArray` for the list of Players, where rows are added and removed;
- `useWatch` to redraw when a field changes that others depend on, such as the Recording Mode;
- Zod's `superRefine` for rules that involve several fields, such as "a competitive Play needs two Players".

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
| `TextField`, `TextAreaField`, `CheckboxField`, `RadioField` | A labelled input, text box, checkbox or set of choices, with hint and error |
| `FormProblem` | Why the server refused a submission |
| `PageMessage` | A page that only has something to say |

The components in `components/ui/` are shadcn/ui's, added with its command line tool. Leave those as generated where possible, and build on top of them.

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

`backend/games/calico` is the example to copy. A module has three parts: an identity, a description of its Game's score pad, and its texts.

1. Copy `backend/games/calico` to `backend/games/<key>` and rename the artifact to `game-<key>`.
2. Give the module a newly generated UUID. It is stored with every Play, so it never changes afterwards ([ADR 0012](adr/0012-game-module-uuid-and-key.md)).
3. Name the module's class in its `META-INF/services` file.
4. Describe the Game in `scorePad()`: how many can play, its Variants, the rows of its score pad as Score Categories, and its Tie-breakers. Take all of it from the Game's official rulebook, and say in the class's Javadoc where each rule comes from.
5. Extend `StandardGameModule` if the Game is scored the usual way: every Player adds up the rows of the pad and the highest total wins. The scoring then follows from the description, and there is no `score` to write. A Game that works differently implements `GameModule` itself, as the Generic Module does. Its `score` must depend on the entry alone, because a Recalculation runs it again over the stored entry.
6. Add `i18n/<key>_en.properties`, and the same for `nl` and `hu`. English needs a text for everything the score pad names, or the application refuses to start:

   | Text | For |
   |---|---|
   | `<key>.name` | the Game, as the catalogue lists it |
   | `<key>.variant.<variant>` | a Variant; `<key>.variant.<variant>.hint` adds a line under it, and is optional |
   | `<key>.variant.<variant>.<option>` | each option of a Variant that is a choice |
   | `<key>.category.<category>` | a row of the score pad |
   | `<key>.tie` | how the Game decides a tie, when the recorder is asked who won one |
   | `<key>.invalid.<reason>` | a reason of the module's own for refusing an entry |

7. Test the module with plain unit tests, as `CalicoGameModuleTests` does. A Game Module needs no Spring and no database. Use a scoring example from the rulebook as one of the tests.
8. Register it in three places: as a `<module>` and under `<dependencyManagement>` in `backend/pom.xml`, and as a `runtime` dependency in `backend/app/pom.xml`.

Nothing else changes. When the application next starts, the Game is in the catalogue, and the frontend draws its form from the description.

**Ties.** List as `tieBreakers` the Tie-breakers that can be worked out from what is entered. Set `recorderDecidesTies` when the Game decides what is left by something that is not on the pad; the form then asks the recorder who won, and shows `<key>.tie` as the explanation. Calico is such a Game: its first Tie-breaker is the number of cat tokens, and the pad only has the cats' points.

**Reasons for refusing an entry.** `StandardGameModule` refuses what is wrong in the same way for every Game, such as too few Players or a value a row doesn't allow. Those are the `Standard` reasons of `InvalidPlayException`, and the frontend has their texts, under `plays.invalid` in its translation files. A module only writes texts for reasons of its own.

## Continuous integration

`.github/workflows/ci.yml` runs on every push. It checks and tests the frontend, builds it, builds and tests the backend, and builds the container image. On `main` it also publishes the image to GitHub Container Registry as `ghcr.io/oliverjuhasz/board-keeper`, tagged `latest` and `sha-<commit>`.

## Tooling notes

- **TypeScript is held at 5.9.** The tools that generate the API types (openapi-typescript) and lint TypeScript (typescript-eslint) do not support TypeScript 7 yet.
- **jOOQ code generation uses three Maven plugins** in `backend/app/pom.xml`: one starts PostgreSQL with Testcontainers, Flyway's applies the migrations, and jOOQ's generates the code. A single plugin that does all three exists (`testcontainers-jooq-codegen-maven-plugin`), but its last release was in April 2024 and it is built against Flyway 9 and Testcontainers 1.19.
