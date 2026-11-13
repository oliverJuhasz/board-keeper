package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import io.github.oliverjuhasz.boardkeeper.access.CurrentAccount;
import io.github.oliverjuhasz.boardkeeper.access.Permission;
import io.github.oliverjuhasz.boardkeeper.language.Language;
import io.github.oliverjuhasz.boardkeeper.players.Player;
import io.github.oliverjuhasz.boardkeeper.players.Players;
import io.github.oliverjuhasz.boardkeeper.problems.Problem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Signing in, signing out, and asking who is signed in. */
@RestController
@RequestMapping("/api/session")
@Tag(name = "Session")
class SessionController {

    private final AuthenticationManager authenticationManager;
    private final SignInSessions sessions;
    private final SessionResponses responses;

    SessionController(AuthenticationManager authenticationManager, SignInSessions sessions, SessionResponses responses) {
        this.authenticationManager = authenticationManager;
        this.sessions = sessions;
        this.responses = responses;
    }

    @GetMapping
    @Operation(
            summary = "Who is signed in",
            description = "Open to everyone. Without a sign-in the answer has no account. "
                    + "The frontend calls this first: the answer also sets the XSRF-TOKEN cookie.",
            operationId = "getSession")
    SessionResponse current(
            @AuthenticationPrincipal CurrentAccount account, @Parameter(hidden = true) CsrfToken csrfToken) {
        // Asking for the token's value is what makes Spring Security send the cookie.
        csrfToken.getToken();
        return account == null ? responses.signedOut() : responses.of(account);
    }

    @PostMapping
    @Operation(summary = "Sign in", operationId = "signIn")
    SessionResponse signIn(
            @Valid @RequestBody SignInRequest body, HttpServletRequest request, HttpServletResponse response) {
        Authentication checked;
        try {
            checked = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(body.username().strip(), body.password()));
        } catch (DisabledException e) {
            throw new Problem(HttpStatus.UNAUTHORIZED, "session.accountDisabled");
        } catch (AuthenticationException e) {
            // Deliberately the same answer for an unknown username and a wrong password.
            throw new Problem(HttpStatus.UNAUTHORIZED, "session.badCredentials");
        }
        UUID accountId = UUID.fromString(checked.getName());
        sessions.start(accountId, body.staySignedIn(), request, response);
        return responses.of(accountId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Sign out", operationId = "signOut")
    void signOut(HttpServletRequest request, HttpServletResponse response) {
        sessions.end(request, response);
    }

    record SignInRequest(
            @NotBlank @Size(max = 30) String username,
            @NotBlank @Size(max = AccountRules.MAX_PASSWORD_LENGTH) String password,
            @Schema(
                            description = "Stay signed in on this device for 90 days after the last visit",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    boolean staySignedIn) {}

    @Schema(name = "Session", requiredProperties = "passkeys")
    record SessionResponse(
            @Schema(description = "The signed-in Account. Absent when nobody is signed in.") AccountResponse account,
            @Schema(description = "Whether this installation offers passkeys") boolean passkeys) {}

    @Schema(
            name = "CurrentAccount",
            requiredProperties = {"id", "username", "playerId", "playerName", "language", "permissions"})
    record AccountResponse(
            UUID id, String username, UUID playerId, String playerName, Language language, Set<Permission> permissions) {

        static AccountResponse of(CurrentAccount account, Player player, Language language) {
            return new AccountResponse(
                    account.accountId(),
                    account.username(),
                    player.id(),
                    player.name(),
                    language,
                    new TreeSet<>(account.permissions()));
        }
    }
}
