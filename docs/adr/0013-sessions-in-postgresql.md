# Signed-in sessions are stored in PostgreSQL

A signed-in session is a row in PostgreSQL, kept there by Spring Session. "Stay signed in" is the same session with a 90-day idle limit and a cookie that outlives the browser, set up by Spring Session's own remember-me support (`SpringSessionRememberMeServices`). Sessions therefore survive application updates, and ending one (signing out, a changed password, a disabled Account) means deleting rows.

## Considered options

Spring Security has two kinds of "remember me" of its own, which keep sessions in memory and sign the person back in from a second cookie.

- **Hash-based, the default.** The cookie is a signature over the username, the password hash, an expiry date and a secret key. It needs no storage. We rejected it because the key has to be configured for every installation (without one it is random at startup, and every restart signs everyone out), its lifetime is fixed at sign-in instead of renewing with use, and the server cannot end the sign-in of one device.
- **Database-backed.** Each use replaces the cookie's token, and a token that turns up twice is treated as theft: all of that person's tokens are deleted. A single-page app sends several requests at once, so after a session has run out, two of them present the same token and the person is signed out everywhere. Spring Security has a long-open request about this (spring-projects/spring-security issue 3079).

## Consequences

Session contents are stored as serialized Java objects. To keep them readable across updates, a session holds only the Account's id (`SignedIn`). Everything else about the Account, including its Permissions, is read from the database on every request, which also makes a change of Roles take effect at once. If an update does change what is stored, people have to sign in again.

Every API request reads its session from the database.
