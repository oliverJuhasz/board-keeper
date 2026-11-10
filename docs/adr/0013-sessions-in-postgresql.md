# Signed-in sessions are stored in PostgreSQL

A signed-in session is a row in PostgreSQL, kept there by Spring Session. "Stay signed in" is the same session with a 90-day idle limit and a cookie that outlives the browser. Sessions therefore survive application updates, and ending one (signing out, a changed password, a disabled Account) means deleting rows.

We rejected Spring Security's own "remember me". Its database-backed form replaces its token on every use. A single-page app sends several requests at once, so after a session has run out, two requests present the same old token, the second one is treated as theft, and the person is signed out on every device.

## Consequences

Session contents are stored as serialized Java objects. To keep them readable across updates, a session holds only the Account's id (`SignedIn`). Everything else about the Account, including its Permissions, is read from the database on every request, which also makes a change of Roles take effect at once. If an update does change what is stored, people have to sign in again.
