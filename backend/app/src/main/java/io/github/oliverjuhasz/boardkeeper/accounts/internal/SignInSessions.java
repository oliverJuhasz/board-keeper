package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.security.web.authentication.SpringSessionRememberMeServices;
import org.springframework.stereotype.Component;

/** Starts and ends the session that keeps an Account signed in. */
@Component
class SignInSessions {

    /** How long "Stay signed in" lasts without a visit. Every visit starts the count again. */
    static final Duration STAY_SIGNED_IN = Duration.ofDays(90);

    private final SecurityContextRepository contextRepository;
    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;
    private final SessionAuthenticationStrategy newSessionId = new ChangeSessionIdAuthenticationStrategy();
    private final SecurityContextLogoutHandler signOut = new SecurityContextLogoutHandler();
    private final SpringSessionRememberMeServices staySignedIn = new SpringSessionRememberMeServices();

    SignInSessions(
            SecurityContextRepository contextRepository,
            FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
        this.contextRepository = contextRepository;
        this.sessionRepository = sessionRepository;
        // We decide ourselves, from the sign-in request, whether to call it at all.
        staySignedIn.setAlwaysRemember(true);
        staySignedIn.setValiditySeconds((int) STAY_SIGNED_IN.toSeconds());
    }

    void start(UUID accountId, boolean staySignedIn, HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication =
                UsernamePasswordAuthenticationToken.authenticated(new SignedIn(accountId), null, List.of());

        // Give the session a new id, so that an id someone planted in the browser beforehand is worthless.
        newSessionId.onAuthentication(authentication, request, response);

        request.getSession(true);
        if (staySignedIn) {
            // Spring Session's own "remember me": it lets this session sit unused for 90 days and
            // tells the cookie to outlive the browser being closed (see sessionCookieSerializer).
            this.staySignedIn.loginSuccess(request, response, authentication);
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        // Since Spring Security 6 nothing is saved to the session unless we ask for it.
        contextRepository.saveContext(context, request, response);
    }

    /**
     * Signs an Account out everywhere, except in the session making this request, if there is
     * one. Used when a password changes or an Account is disabled. Spring Session files every
     * session under its Account's id ({@link SignedIn#getName()}), which is how we find them.
     */
    void endAllOf(UUID accountId, HttpServletRequest request) {
        HttpSession current = request.getSession(false);
        sessionRepository.findByPrincipalName(accountId.toString()).keySet().stream()
                .filter(sessionId -> current == null || !sessionId.equals(current.getId()))
                .forEach(sessionRepository::deleteById);
    }

    void end(HttpServletRequest request, HttpServletResponse response) {
        signOut.logout(request, response, SecurityContextHolder.getContext().getAuthentication());
    }
}
