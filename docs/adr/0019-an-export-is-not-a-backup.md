---
status: accepted
---

# An export holds the shared record, not the installation

Board Keeper exports its Games, Players and Plays to a file, to use them elsewhere or to import them into another installation. The file leaves out everything about Accounts: usernames, password hashes, Roles, Claims, Passkeys, API Keys, sessions, Invites and Reset Links. It also leaves out the statistics, which are worked out from the Plays. So an export is not a backup. A backup is made with `pg_dump`, as the self-hosting guide describes.

We rejected an export that copies a whole installation. It would put password hashes and the hashes of API Keys and Reset Links into a file that is meant to be handed around, and it would have to follow every migration, while `pg_dump` already copies everything. We also left out which Players have an Account. Without it, the file names no one beyond the Players' names, and says nothing about who uses the installation.

## Consequences

- An installation filled from an import has none of the source's Accounts. People are invited again, and each claims their Player.
