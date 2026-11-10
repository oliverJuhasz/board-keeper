package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import java.io.Serializable;
import java.util.UUID;
import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * What a session remembers about who signed in: the Account's id and nothing else. It is
 * kept small and simple on purpose, because it is stored in the database with the session
 * and has to stay readable across application updates.
 *
 * <p>Everything else about the Account is read fresh on every request; see
 * {@link AccountRefreshFilter}.
 */
record SignedIn(UUID accountId) implements AuthenticatedPrincipal, Serializable {

    /** Spring Session files the session under this name, which lets us find an Account's sessions. */
    @Override
    public String getName() {
        return accountId.toString();
    }
}
