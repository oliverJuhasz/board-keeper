---
status: accepted
---

# Managing Roles, Accounts and Invites is for Admins only

Three Permissions belong to the Admin Role alone and cannot be put into any other Role: managing Roles, managing Accounts, and managing Invites (which includes Reset Links). To let someone else do these things, make them an Admin.

This replaces ADR 0014. There, the three could be given to any Role, and a rule that "nobody hands out more than they hold" kept that from being a way to become an Admin. The rule worked, but it cost checks throughout the backend and greyed-out controls in the interface, all to allow something a small group of players does not need: a person who may invite others or edit Roles without being trusted as an Admin.

Fine-grained Permissions stay for everyday actions: creating, renaming and removing Players, and recording and changing Plays (ADR 0008).

## Consequences

An Admin can do anything to any Account, including another Admin's. The only protections left are that the last Admin who can sign in keeps the Admin Role, and that nobody can disable their own Account.
