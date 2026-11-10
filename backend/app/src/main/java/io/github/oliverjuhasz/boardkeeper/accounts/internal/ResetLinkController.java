package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import io.github.oliverjuhasz.boardkeeper.access.CurrentAccount;
import io.github.oliverjuhasz.boardkeeper.access.Permission;
import io.github.oliverjuhasz.boardkeeper.access.RequiresPermission;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.Passwords.NewResetLink;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.SessionController.SessionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Reset Links: creating one for someone, and using one to choose a new password. */
@RestController
@Tag(name = "Reset Links")
class ResetLinkController {

    private final Passwords passwords;
    private final SignInSessions sessions;
    private final SessionResponses responses;

    ResetLinkController(Passwords passwords, SignInSessions sessions, SessionResponses responses) {
        this.passwords = passwords;
        this.sessions = sessions;
        this.responses = responses;
    }

    @PostMapping("/api/reset-links")
    @RequiresPermission(Permission.MANAGE_INVITES)
    @Operation(
            summary = "Create a Reset Link for the Account of a Player",
            description = "Replaces any earlier Reset Link for the same Account. The answer holds the "
                    + "link's token, which cannot be asked for again. Only possible for an Account that "
                    + "holds no Permission its creator lacks.",
            operationId = "createResetLink")
    NewResetLinkResponse create(@AuthenticationPrincipal CurrentAccount account, @Valid @RequestBody ResetLinkRequest body) {
        NewResetLink created = passwords.createResetLink(body.playerId(), account);
        return new NewResetLinkResponse(created.token(), created.expiresAt());
    }

    @GetMapping("/api/password-reset/{token}")
    @Operation(summary = "Whose password a Reset Link is for", description = "Open to everyone.", operationId = "getPasswordReset")
    PasswordResetResponse open(@PathVariable String token) {
        return new PasswordResetResponse(passwords.open(token));
    }

    @PostMapping("/api/password-reset/{token}")
    @Operation(
            summary = "Choose a new password with a Reset Link, and sign in",
            description = "Open to everyone. Uses the link up and signs the Account out everywhere else.",
            operationId = "resetPassword")
    SessionResponse reset(
            @PathVariable String token,
            @Valid @RequestBody NewPasswordRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        UUID accountId = passwords.reset(token, body.password(), request);
        sessions.start(accountId, true, request, response);
        return responses.of(accountId);
    }

    record ResetLinkRequest(@NotNull UUID playerId) {}

    @Schema(
            name = "NewResetLink",
            requiredProperties = {"token", "expiresAt"})
    record NewResetLinkResponse(
            @Schema(description = "The secret part of the link: the person opens /reset-password/{token}") String token,
            OffsetDateTime expiresAt) {}

    @Schema(name = "PasswordReset", requiredProperties = "username")
    record PasswordResetResponse(String username) {}

    record NewPasswordRequest(
            @NotNull @Size(min = AccountRules.MIN_PASSWORD_LENGTH, max = AccountRules.MAX_PASSWORD_LENGTH)
                    String password) {}
}
