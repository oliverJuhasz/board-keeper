# Board Keeper

A self-hosted app for recording board game plays and scores, for personal use and friends. It runs on a home NAS.

- Domain language: [`CONTEXT.md`](CONTEXT.md)
- Architecture decisions: [`docs/adr/`](docs/adr/)
- Scope and milestones: [`docs/roadmap.md`](docs/roadmap.md)
- Building, running and changing it: [`docs/development.md`](docs/development.md)

## Stack

Board Keeper is a learning project first ([ADR 0001](docs/adr/0001-learning-project.md)), so some choices are heavier than the app strictly needs.

**Backend:** Java 25, Spring Boot 4, Spring Modulith, Spring Security (password and passkeys), jOOQ, Flyway, PostgreSQL, built with Maven. The API is described with OpenAPI (springdoc).

**Frontend:** a React single-page app with TypeScript and Vite, installable as a PWA (online only).

| Purpose | Library |
|---|---|
| Navigation | React Router |
| Server data | TanStack Query |
| API types and client | openapi-typescript, openapi-fetch, openapi-react-query |
| Browser-only state | React's built-in hooks (Zustand if needed) |
| Translations | react-i18next: English (default), Dutch, Hungarian |
| Forms and validation | React Hook Form, Zod |
| Styling and components | Tailwind CSS v4, shadcn/ui (on Base UI) |
| PWA | vite-plugin-pwa |
| Tests | Vitest, React Testing Library, Mock Service Worker; Playwright end-to-end tests once the frontend is complete |
| Lint and format | ESLint, Prettier |

**Deployment:** GitHub Actions builds and tests every push and publishes the container image to GitHub Container Registry. It runs as one app container (Spring Boot serves both the API and the built frontend) plus one PostgreSQL container. The repository provides the image, an example compose file and a self-hosting guide. Anything specific to one installation is out of scope: network access, reverse proxy, automatic updates and backup scheduling. On first start, with no accounts yet, the app writes a one-time link to its log for creating the first Admin.

**Building needs Docker**, because jOOQ code is generated from a throwaway PostgreSQL ([ADR 0007](docs/adr/0007-flyway-and-jooq-codegen-from-real-postgres.md)).

## Status

Milestone M0, the skeleton, is in place: the backend, the frontend, the container image and CI all build and run, and one page shows data from the API. Milestone M1 is under way: the interface is available in English, Dutch and Hungarian. Until M1 adds sign-in, the app has no access control.
