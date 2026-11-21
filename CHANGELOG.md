# Changelog

All notable changes to Board Keeper are recorded here, newest first: what someone who uses Board Keeper, or runs it, would notice.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). [`docs/development.md`](docs/development.md#releases) says what each part of a version number means, and how a release is made.

## [Unreleased]

### Added

- The Players of a Play can be put in another order while it is recorded or edited, by dragging or from the keyboard. The Score Pad's columns follow that order, and the Play keeps it.
- A Game Module for Last Bastion, a Cooperative Game for one to four Players, each with a Hero. Its Difficulty is a Variant, and only a victory is scored: from the Life and Call to Arms tokens left, the dead heroes and the Bastion tiles in the Grasp of Evil.

### Changed

- "Record another play" on a Play's page starts the form with that Play's Players, Setup, date and location, so a second Play on the same day takes only its scores.
- When a Play is recorded or edited, its Expansions come before its Players, because an Expansion can decide how many can play and what can be chosen for each Player.
- 7 Wonders offers Roma and Abu Simbel only in a Play with Leaders, and Byzantium and Petra only with Cities. A Play recorded with one of them without its Expansion is refused when it is recalculated, until the Expansion is ticked or the Wonder board is changed.

### Fixed

- Adding a Player to a Play no longer moves the cursor to their points. It stays on the list of Players, ready for the next one, until nobody more can be added. Closing the dialog for a new Player brings it back there too.
- Closing the dialog for adding a Player, a Game, an API Key or a Passkey leaves the cursor on the button that opened it, instead of at the top of the page.

## [1.0.0] - 2026-11-16

### Added

- Accounts that sign in with a password or a Passkey. The first Admin is created through a one-time Setup Link, everyone else through an Invite. A forgotten password is solved with a Reset Link.
- Players, who exist with or without an Account.
- Roles made of fine-grained Permissions, with pages for managing Roles and Accounts.
- A catalogue of Games, each with how its Plays are recorded by default.
- Recording Plays of any Game in the catalogue: competitive or Cooperative, in points or with only the winner, with Placings and shared wins.
- Game Modules, which bring a Game with a Score Pad of its own, its Expansions, Variants, Player Setup, Tie-breakers, Victory Types and way of playing alone: 7 Wonders (with Leaders and Cities), 7 Wonders Duel (with Pantheon and Agora), Calico, Hadara, Harmonies, Spirit Island and Wyrmspan.
- A start page that gets to recording a Play in one or two taps: recent Plays to play again, and the most recently played Games first.
- The play history, filterable by "my plays", Game, Player and date, with editing and deleting Plays according to Permissions.
- Recalculation of a Play, or of every Play of a Game by an Admin.
- Statistics per Game and per Player: wins and win rates, best and average scores, average Placings, and averages per Score Category.
- Installing Board Keeper as an app on a phone's home screen, with a notice when a new version is ready.
- Recording Plays without a connection: they wait on the device and are sent once Board Keeper can be reached.
- API Keys, with which scripts and AI agents use Board Keeper as an Account, and the API described in Swagger UI.
- The interface in English, Dutch and Hungarian.
- An About page, opened from the account menu: the running version, where its source code is, and the licence.
- A container image, an example Docker Compose file and a self-hosting guide. Images are tagged with their version, `latest` for the newest release, `snapshot` for the newest change, and `sha-…` for each change.

[unreleased]: https://github.com/oliverJuhasz/board-keeper/compare/1.0.0...HEAD
[1.0.0]: https://github.com/oliverJuhasz/board-keeper/releases/tag/1.0.0
