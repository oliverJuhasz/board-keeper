---
status: accepted
---

# An Export holds the shared record, not the Installation

An Export holds an Installation's Games, Players and Plays, to use them elsewhere or to bring them into another Installation by an Import. It leaves out everything about Accounts: usernames, password hashes, Roles, Claims, Passkeys, API Keys, sessions, Invites and Reset Links. It also leaves out the statistics, which are worked out from the Plays. So an Export is no backup of the Installation. A backup is made with `pg_dump`, as the self-hosting guide describes. Still, if the database is lost, an Import of an Export brings back the Games, Players and Plays, and only the Accounts have to be set up again.

We rejected an Export that copies a whole Installation. It would put password hashes and the hashes of API Keys and Reset Links into a file that is meant to be handed around, and it would have to follow every migration, while `pg_dump` already copies everything. We also left out which Players have an Account. Without it, the file names no one beyond the Players' names, and says nothing about who uses the Installation.

## Consequences

- An Installation filled by an Import has none of the source's Accounts. People are invited again, and each claims their Player.
- An Export can't be made by a scheduled job, because API Keys can't make one (ADR 0017). Scheduled backups stay with `pg_dump`.
