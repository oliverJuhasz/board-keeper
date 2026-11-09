# Board Keeper

A self-hosted app for recording board game plays and scores, for personal use and friends. It runs on a home NAS.

- Domain language: [`CONTEXT.md`](CONTEXT.md)
- Architecture decisions: [`docs/adr/`](docs/adr/)

## Stack

Board Keeper is a learning project first ([ADR 0001](docs/adr/0001-learning-project.md)), so some choices are heavier than the app strictly needs.

**Backend:** Java 25, Spring Boot 4, Spring Modulith, Spring Security (password and passkeys), jOOQ, Flyway, PostgreSQL, built with Maven. The API is described with OpenAPI (springdoc).

**Frontend:** a React single-page app with TypeScript and Vite, installable as a PWA (online only).

| Purpose | Library |
|---|---|
| Navigation | React Router |
| Server data | TanStack Query |
| API types and client | openapi-typescript, openapi-fetch, openapi-react-query |
| Browser-only state | Redux Toolkit |
| Forms and validation | React Hook Form, Zod |
| Styling and components | Tailwind CSS v4, shadcn/ui |
| PWA | vite-plugin-pwa |
| Tests | Vitest, React Testing Library, Playwright |
| Lint and format | ESLint, Prettier |

**Deployment:** one app container (Spring Boot serves both the API and the built frontend) plus one PostgreSQL container. How users reach it (reverse proxy, VPN, DNS) is outside this repository's scope. The app expects to sit behind a reverse proxy that handles HTTPS.

**Building needs Docker**, because jOOQ code is generated from a throwaway PostgreSQL ([ADR 0007](docs/adr/0007-flyway-and-jooq-codegen-from-real-postgres.md)).
