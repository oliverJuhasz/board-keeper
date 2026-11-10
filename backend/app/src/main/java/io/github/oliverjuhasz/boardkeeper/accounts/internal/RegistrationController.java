package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import io.github.oliverjuhasz.boardkeeper.accounts.internal.Invites.OpenInvite;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.SessionController.SessionResponse;
import io.github.oliverjuhasz.boardkeeper.language.Language;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Registering an Account with an Invite's link. Open to everyone: the token is the key. */
@RestController
@RequestMapping("/api/registration/{token}")
@Tag(name = "Registration")
class RegistrationController {

    private final Invites invites;
    private final SignInSessions sessions;
    private final SessionResponses responses;

    RegistrationController(Invites invites, SignInSessions sessions, SessionResponses responses) {
        this.invites = invites;
        this.sessions = sessions;
        this.responses = responses;
    }

    @GetMapping
    @Operation(summary = "Who an Invite's link is for", operationId = "getRegistration")
    RegistrationResponse open(@PathVariable String token) {
        OpenInvite invite = invites.open(token);
        return new RegistrationResponse(invite.player().name(), invite.expiresAt());
    }

    @PostMapping
    @Operation(summary = "Register the Account an Invite offers, and sign in as it", operationId = "register")
    SessionResponse register(
            @PathVariable String token,
            @Valid @RequestBody RegistrationRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        UUID accountId = invites.accept(token, body.username(), body.password(), body.language());
        sessions.start(accountId, true, request, response);
        return responses.of(accountId);
    }

    @Schema(
            name = "Registration",
            requiredProperties = {"playerName", "expiresAt"})
    record RegistrationResponse(
            @Schema(description = "The Player the new Account will claim") String playerName, OffsetDateTime expiresAt) {}

    record RegistrationRequest(
            @NotNull @Pattern(regexp = AccountRules.USERNAME_PATTERN) String username,
            @NotNull @Size(min = AccountRules.MIN_PASSWORD_LENGTH, max = AccountRules.MAX_PASSWORD_LENGTH)
                    String password,
            @NotNull Language language) {}
}
