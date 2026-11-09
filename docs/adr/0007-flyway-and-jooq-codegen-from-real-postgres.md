# Flyway migrations, with jOOQ code generated from a real PostgreSQL

The schema changes only through Flyway migrations written in plain PostgreSQL SQL. During the build, Testcontainers starts a throwaway PostgreSQL, Flyway applies the migrations, and jOOQ generates its Java code from the resulting database, so the generated code always matches what PostgreSQL actually built.

We chose Flyway over Liquibase, which the author knows better, partly to learn it (ADR 0001). Liquibase's main advantages, database-neutral changes and free rollback, are also worth little once we are committed to PostgreSQL. We rejected jOOQ's option of reading the SQL files without a database, because its parser does not understand every PostgreSQL feature.

## Consequences

Building the backend needs Docker, both locally and in CI.
