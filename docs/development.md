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
  games/generic/     the Generic Module; every Game Module is a folder under games/
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

This starts the backend on http://localhost:8080 with a throwaway PostgreSQL in Docker, so there is nothing to configure. The database is empty on every start.

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

### How Game Modules are found

Game Modules are plain Java, with no Spring. The core finds them with Java's `ServiceLoader`: each module's jar names its class in `META-INF/services/io.github.oliverjuhasz.boardkeeper.gameapi.GameModule`. At startup, `GameModules` checks them all, and the application refuses to start if two share an id or a key, or if one has no English translation file.

### Changing the database

1. Add a file to `backend/app/src/main/resources/db/migration`, named `V<next number>__<what_it_does>.sql`, with two underscores after the number. Never edit a migration that has been applied to a real database.
2. Build. The build applies every migration to a throwaway PostgreSQL and regenerates the jOOQ classes into `backend/app/target/generated-sources/jooq`. In an IDE, reload the Maven project or run "Generate Sources".
3. Use the regenerated classes, starting from `Tables`.

### Changing the API

`api/openapi.json` describes the API, and the frontend's TypeScript types are generated from it. A backend test fails when that file no longer matches the real API, so the two cannot drift apart unnoticed.

1. Change the controller.
2. In `backend/`, run `./mvnw verify -Dboardkeeper.openapi.update=true` to rewrite `api/openapi.json`.
3. Commit the file along with your change.
4. In `frontend/`, `npm run generate:api` regenerates `src/api/schema.d.ts`. It also runs by itself before `npm run dev` and `npm run build`. TypeScript then reports every place in the frontend that has to follow the change.

### Adding a user-interface component

shadcn/ui components are not a library you import. Their source is copied into the project, where you can read and change it:

```bash
npx shadcn@latest add dialog
```

This writes `frontend/src/components/ui/dialog.tsx`. Run `npm run format` afterwards.

### Adding a Game Module

The contract is only identity for now; milestone M3 adds scoring.

1. Copy `backend/games/generic` to `backend/games/<key>` and rename the artifact to `game-<key>`.
2. Give the module a newly generated UUID. It is stored with every Play, so it never changes afterwards ([ADR 0012](adr/0012-game-module-uuid-and-key.md)).
3. Name the module's class in its `META-INF/services` file.
4. Add `i18n/<key>_en.properties`, and the same for `nl` and `hu`.
5. Register it in three places: as a `<module>` and under `<dependencyManagement>` in `backend/pom.xml`, and as a `runtime` dependency in `backend/app/pom.xml`.

## Continuous integration

`.github/workflows/ci.yml` runs on every push. It checks and tests the frontend, builds it, builds and tests the backend, and builds the container image. On `main` it also publishes the image to GitHub Container Registry as `ghcr.io/oliverjuhasz/board-keeper`, tagged `latest` and `sha-<commit>`.

## Tooling notes

- **TypeScript is held at 5.9.** The tools that generate the API types (openapi-typescript) and lint TypeScript (typescript-eslint) do not support TypeScript 7 yet.
- **jOOQ code generation uses three Maven plugins** in `backend/app/pom.xml`: one starts PostgreSQL with Testcontainers, Flyway's applies the migrations, and jOOQ's generates the code. A single plugin that does all three exists (`testcontainers-jooq-codegen-maven-plugin`), but its last release was in April 2024 and it is built against Flyway 9 and Testcontainers 1.19.
