# Changelog

All notable changes to Board Keeper are recorded here, newest first: what someone who uses Board Keeper, or runs it, would notice.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). [`docs/development.md`](docs/development.md#releases) says what each part of a version number means, and how a release is made.

## [Unreleased]

## [1.0.0] - 2026-11-27

### Added

- Accounts that sign in with a password or a Passkey. The first Admin is created through a one-time Setup Link, everyone else through an Invite. A forgotten password is solved with a Reset Link.
- Players, who exist with or without an Account.
- Roles made of fine-grained Permissions, with pages for managing Roles and Accounts.
- A catalogue of Games, each with how its Plays are recorded by default.
- Recording Plays of any Game in the catalogue: competitive or Cooperative, in points or with only the winner, with Placings and shared wins.
- Putting the Players of a Play in another order while it is recorded or edited, by dragging or from the keyboard. The Score Pad's columns follow that order, and the Play keeps it.
- Game Modules, which bring a Game with a Score Pad of its own, its Expansions, Variants, Player Setup, Tie-breakers, Victory Types and way of playing alone: 7 Wonders (with Leaders and Cities), 7 Wonders Duel (with Pantheon and Agora), Calico, Hadara, Harmonies, Last Bastion, Lords of Waterdeep (with Scoundrels of Skullport), Spirit Island and Wyrmspan.
- A start page that gets to recording a Play in one or two taps: recent Plays to play again, and the most recently played Games first.
- The play history, filterable by "my plays", Game, Player and date, with editing and deleting Plays according to Permissions.
- Recalculation of a Play, or of every Play of a Game by an Admin.
- Statistics per Game and per Player: wins and win rates, best and average scores, average Placings, and averages per Score Category.
- Installing Board Keeper as an app on a phone's home screen, with a notice when a new version is ready.
- Recording Plays without a connection: they wait on the device and are sent once Board Keeper can be reached.
- API Keys, with which scripts and AI agents use Board Keeper as an Account, and the API described in Swagger UI.
- Exports of every Game, Player and Play as one file, for Admins, from "Export and import" in the account menu: to use elsewhere, or to import into another Installation. An Export holds nothing about Accounts, so it is no backup of the Installation.
- Imports of an Export into another Installation, from the same page. A review first says what it would add and which Plays this Installation can't take, and asks who each of its Players is here. Plays keep their Results and who recorded them, and a Play that is already there is left as it is.
- The interface in English, Dutch and Hungarian.
- An About page, opened from the account menu: the running version, where its source code is, and the licence.
- A container image, an example Docker Compose file and a self-hosting guide. Images are tagged with their version, `latest` for the newest release, `snapshot` for the newest change, and `sha-…` for each change.

[unreleased]: https://github.com/oliverJuhasz/board-keeper/compare/1.0.0...HEAD
[1.0.0]: https://github.com/oliverJuhasz/board-keeper/releases/tag/1.0.0
