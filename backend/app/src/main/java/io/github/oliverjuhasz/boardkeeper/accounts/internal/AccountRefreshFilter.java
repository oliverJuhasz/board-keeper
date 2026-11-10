package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import io.github.oliverjuhasz.boardkeeper.access.CurrentAccount;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * On every API request, replaces what the session remembers (only the Account's id) with the
 * Account as it is now: its {@link CurrentAccount}, with each Permission as an "authority"
 * that Spring Security can check.
 *
 * <p>Because of this, giving or taking a Role works at once, and a disabled Account is signed
 * out by its very next request. The price is one small query per request.
 */
class AccountRefreshFilter extends OncePerRequestFilter {

    private final CurrentAccounts currentAccounts;
    private final SignInSessions sessions;

    AccountRefreshFilter(CurrentAccounts currentAccounts, SignInSessions sessions) {
        this.currentAccounts = currentAccounts;
        this.sessions = sessions;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + "/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication stored = SecurityContextHolder.getContext().getAuthentication();
        if (stored != null && stored.getPrincipal() instanceof SignedIn signedIn) {
            Optional<CurrentAccount> current = currentAccounts.load(signedIn.accountId());
            if (current.isPresent()) {
                // Set for this request only; the session keeps its small SignedIn.
                SecurityContext fresh = SecurityContextHolder.createEmptyContext();
                fresh.setAuthentication(authenticationOf(current.get()));
                SecurityContextHolder.setContext(fresh);
            } else {
                // The Account was disabled or no longer exists.
                sessions.end(request, response);
            }
        }
        chain.doFilter(request, response);
    }

    static Authentication authenticationOf(CurrentAccount account) {
        List<SimpleGrantedAuthority> authorities = account.permissions().stream()
                .map(permission -> new SimpleGrantedAuthority(permission.name()))
                .toList();
        return UsernamePasswordAuthenticationToken.authenticated(account, null, authorities);
    }
}
