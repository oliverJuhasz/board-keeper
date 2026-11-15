package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import io.github.oliverjuhasz.boardkeeper.access.CurrentAccount;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Who a request with an API Key acts as. Spring Security keeps an {@link Authentication} for
 * every request; this kind tells a request with an API Key apart from one with a session, which
 * is what the rules that keep API Keys away from signing in check (see
 * {@link SecurityConfiguration}).
 *
 * <p>Its principal is the {@link CurrentAccount}, as for a session, so controllers receive it
 * the same way. It is never stored: a request with an API Key creates no session.
 */
final class ApiKeyAuthentication extends AbstractAuthenticationToken {

    /** Not kept when serialized, which never happens: this lives for one request only. */
    private final transient CurrentAccount account;

    ApiKeyAuthentication(CurrentAccount account) {
        super(account.permissions().stream()
                .map(permission -> new SimpleGrantedAuthority(permission.name()))
                .toList());
        this.account = account;
        setAuthenticated(true);
    }

    static boolean isOne(Authentication authentication) {
        return authentication instanceof ApiKeyAuthentication;
    }

    @Override
    public CurrentAccount getPrincipal() {
        return account;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public String getName() {
        return account.accountId().toString();
    }
}
