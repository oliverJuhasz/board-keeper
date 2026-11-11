-- Managing Invites, Accounts and Roles is now for Admins only (ADR 0015). The Admin Role holds
-- every Permission by definition and has no rows here, so this only takes these three away
-- from any other Role that was given them.
delete
from role_permission
where permission in ('MANAGE_INVITES', 'MANAGE_ACCOUNTS', 'MANAGE_ROLES');
