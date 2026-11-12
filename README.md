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

Milestones M0, M1 and M2 are done. Board Keeper builds, runs as a container image, and has:

- the interface in English, Dutch and Hungarian
- Accounts with sign-in, created through a first-time Setup Link and through Invites
- Players, who exist with or without an Account
- a catalogue of Games, each with how its Plays are recorded by default
- recording Plays of any Game in the catalogue: competitive or Cooperative, in points or with only the winner, with Placings and shared wins
- the play history, filterable by "my plays", Game, Player and date, with editing and deleting Plays according to Permissions
- Roles made of fine-grained Permissions, with pages for managing Roles and Accounts
- password changes and Reset Links

Milestone M3 comes next: the first Game Modules for real games (Calico, Wyrmspan and Harmonies), and with them the full module contract.
