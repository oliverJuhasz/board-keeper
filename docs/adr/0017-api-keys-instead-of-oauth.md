---
status: accepted
---

# Programs use API Keys, not OAuth

Programs such as scripts and AI agents use the API with an API Key: a secret that belongs to one Account and acts as it, with that Account's Permissions as they are at the time of each request. We chose this over making Board Keeper an OAuth authorization server. OAuth lets apps written by other people act for someone after a consent screen, but a circle of friends running their own scripts and agents has no such apps, and OAuth would cost client registration, a consent page, refresh tokens, and signing keys that every installation has to keep.

A leaked API Key must not hand over the whole installation. So an API Key never carries the Admin-only Permissions, and can't change how its Account signs in: its password, its Passkeys and its API Keys. API Keys have no narrower scopes, such as read-only; whatever else the Account may do, its API Keys may do too.

## Consequences

- The Permission "Use API Keys" guards both creating API Keys and using them, so taking it away stops an Account's API Keys at once. No built-in Role but Admin has it to begin with.
- Disabling an Account deletes its API Keys, as it ends its sessions. Changing the password leaves them working.
- An MCP server for AI agents is planned. The connectors of web-based assistants expect OAuth, so the server may reopen this decision.
