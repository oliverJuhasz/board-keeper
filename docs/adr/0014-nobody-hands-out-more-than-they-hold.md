# Nobody can hand out more than they hold

Wherever one Account acts on another Account's access, it can only deal in Permissions it holds itself. This covers:

- putting Permissions into a Role, and changing or deleting a Role
- giving a Role to an Account or taking it away
- disabling an Account
- creating a Reset Link for an Account
- choosing a Role other than Member for an Invite

Without this rule, the Permissions to manage Roles, Accounts or Invites would each be a quiet way to become an Admin: add every Permission to a Role you already have, give yourself the Admin Role, or reset an Admin's password and sign in as them.

We considered leaving the rule out and treating those three Permissions as "Admin in all but name". That would have made them useless for delegating part of an Admin's work, which is the reason fine-grained Permissions exist here (ADR 0008).

## Consequences

Someone who manages Accounts but is no Admin sees some Roles and Accounts they cannot change. The interface greys these out and says why.
