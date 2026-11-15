package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import io.github.oliverjuhasz.boardkeeper.accounts.internal.ApiKeys.Check;
import io.github.oliverjuhasz.boardkeeper.problems.ProblemResponses;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lets a request use the API with an API Key, sent as {@code Authorization: Bearer bk_…},
 * instead of a session (ADR 0017).
 *
 * <p>A request with an API Key acts as the API Key's Account, even if it also carries a session
 * cookie, and nothing about it is stored: it creates no session. An API Key that isn't accepted is
 * answered at once; the request never falls back to the session.
 *
 * <p>Such a request needs no CSRF token. That token exists because a browser sends cookies by
 * itself, even with a request that another website made it send. A browser never adds an
 * {@code Authorization} header by itself, so a request with an API Key cannot be forged that way.
 */
class ApiKeyFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    /**
     * Requests to the API that carry an API Key. Other {@code Authorization} headers, such as
     * one that a reverse proxy adds, are left alone.
     */
    static final RequestMatcher REQUESTS = request ->
            request.getRequestURI().startsWith(request.getContextPath() + "/api/") && secretOf(request) != null;

    private final ApiKeys apiKeys;

    ApiKeyFilter(ApiKeys apiKeys) {
        this.apiKeys = apiKeys;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !REQUESTS.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        switch (apiKeys.check(secretOf(request))) {
            case Check.Accepted(var account) -> {
                // For this request only. Nothing saves it into a session.
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(new ApiKeyAuthentication(account));
                SecurityContextHolder.setContext(context);
                chain.doFilter(request, response);
            }
            case Check.Refused(var status, var code) -> {
                if (status == HttpStatus.UNAUTHORIZED) {
                    // Tells the program that this address wants a bearer token (RFC 6750).
                    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
                }
                ProblemResponses.write(response, status, code);
            }
        }
    }

    /** The API Key in the request's {@code Authorization} header, or null if it has none. */
    private static String secretOf(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        // The name of the scheme ignores capitals (RFC 9110).
        if (header == null || !header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return null;
        }
        String secret = header.substring(BEARER.length()).strip();
        return secret.startsWith(ApiKeys.PREFIX) ? secret : null;
    }
}
