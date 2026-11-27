# Role-based access with fine-grained permissions

What an Account may do is decided by Permissions. They are a fixed list in code, because code checks them. Admins combine Permissions into Roles in the app and give Roles to Accounts.

Permissions distinguish whose data is affected ("edit Plays I recorded", "edit Plays I took part in", "edit any Play"), so checks happen per item, not just per endpoint. A built-in Admin role holds every Permission and can't be removed from the last Account holding it, so nobody can be locked out.

This is more than one group of players needs. It is here to practise Spring Security's authorization (ADR 0001).
