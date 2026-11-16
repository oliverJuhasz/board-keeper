# Board Keeper

A self-hosted app for recording board game plays and scores, for personal use and friends. It runs on a home NAS.

- Domain language: [`CONTEXT.md`](CONTEXT.md)
- Architecture decisions: [`docs/adr/`](docs/adr/)
- Scope and milestones: [`docs/roadmap.md`](docs/roadmap.md)
- Building, running and changing it: [`docs/development.md`](docs/development.md)
- What changed in each release: [`CHANGELOG.md`](CHANGELOG.md)

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

**Deployment:** GitHub Actions builds and tests every push and publishes the container image to GitHub Container Registry: every change on `main` as a snapshot, and every release, which is a git tag such as `1.2.3` ([ADR 0018](docs/adr/0018-releases-are-git-tags.md)). It runs as one app container (Spring Boot serves both the API and the built frontend) plus one PostgreSQL container. The repository provides the image, an example compose file and a [self-hosting guide](docs/self-hosting.md). Anything specific to one installation is out of scope: network access, reverse proxy, automatic updates and backup scheduling. On first start, with no accounts yet, the app writes a one-time link to its log for creating the first Admin. If the only Admin forgets their password, a command run on the container writes a Reset Link for them.

**Building needs Docker**, because jOOQ code is generated from a throwaway PostgreSQL ([ADR 0007](docs/adr/0007-flyway-and-jooq-codegen-from-real-postgres.md)).

## Status

Milestones M0 to M6 are done, which are all the milestones planned for v1. Board Keeper builds, runs as a container image, and has:

- the interface in English, Dutch and Hungarian
- Accounts with sign-in by password or passkey, created through a first-time Setup Link and through Invites
- Players, who exist with or without an Account
- a catalogue of Games, each with how its Plays are recorded by default
- recording Plays of any Game in the catalogue: competitive or Cooperative, in points or with only the winner, with Placings and shared wins
- Game Modules that bring a Game with its own score pad, Expansions, Variants, choices for each Player, tie rules, ways to win and way of playing alone: 7 Wonders (with Leaders and Cities), 7 Wonders Duel (with Pantheon and Agora), Calico, Hadara, Harmonies, Spirit Island and Wyrmspan
- a start page that gets to recording a Play in one or two taps: recent Plays to play again, and the most recently played Games first
- the play history, filterable by "my plays", Game, Player and date, with editing and deleting Plays according to Permissions
- Recalculation: scoring a Play again with its Game Module as it is now, or, for an Admin, every Play of a Game at once
- statistics per Game and per Player: wins and win rates, best and average scores, average Placings, averages per row of a score pad, and Cooperative and Solo Plays on their own
- installing Board Keeper as an app on a phone's home screen, with a notice when a new version is ready
- recording Plays without a connection: they wait on the device and are sent once Board Keeper can be reached
- Roles made of fine-grained Permissions, with pages for managing Roles and Accounts
- API Keys, with which scripts and AI agents use Board Keeper as an Account, and the API described in Swagger UI
- password changes and Reset Links
- an About page with the version that is running, where its source code is, and the licence

What might come after v1 is listed under Later in the [roadmap](docs/roadmap.md).

## Licence

Copyright (C) 2026 Oliver Juhasz

Board Keeper is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. It is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See [`LICENSE`](LICENSE) for the full terms.

In short: anyone may use, change and share Board Keeper, also for money. Whoever shares a copy, or runs a changed copy for others, must offer its source code under the same licence. The About page, opened from the account menu, makes that offer for the running version ([ADR 0016](docs/adr/0016-agpl-licence.md)).

The web app includes libraries under licences of their own, listed in `third-party-licenses.txt` beside it (`/third-party-licenses.txt` on a running installation). The backend's libraries keep their licence files inside their own jars.

Board Keeper is not affiliated with the publishers of the games it scores. The names of the games, and names from them such as those of Spirits and Wonders, belong to their owners and are used only to say which game a module scores.
