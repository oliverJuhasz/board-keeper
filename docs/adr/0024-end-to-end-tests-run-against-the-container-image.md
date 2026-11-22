---
status: accepted
---

# End-to-end tests run against the container image

The Playwright tests run against the container image that CI is about to publish, started with PostgreSQL through the example compose file, `deploy/compose.yaml`. They test what an Installation really runs, including the compose file that self-hosters copy. Nothing is added to the image for testing: the first Admin comes from the real Setup Link in the log, and the tests make their data through the screens, as a person would, except where they need a lot of it at once. Every test starts from the same state: once the setup has created the first Admin and a few Players, PostgreSQL copies the database, and before each test the copy is put back through the database's container, in about a second.

## Considered options

- **The application started from the backend's test sources**, as `spring-boot:test-run` does, with test-only helpers such as the development Admin. Quicker to rerun while writing tests, but the Dockerfile and the compose file would go untested.
- **A fresh Installation for each test file.** Each costs 15 to 20 seconds, almost all of it the application starting.
- **H2 instead of PostgreSQL**, to start faster. The database isn't what is slow, and the migrations and code are PostgreSQL's, with `jsonb` columns and advisory locks.

## Consequences

- Running the tests needs the image first: build the frontend, then the backend, then `docker build`.
- The tests know the database's name and reach it through `docker compose exec`, so changing the database in the compose file can break them.
