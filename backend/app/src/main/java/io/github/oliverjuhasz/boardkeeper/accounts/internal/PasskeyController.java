package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import io.github.oliverjuhasz.boardkeeper.access.CurrentAccount;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.Passkeys.Passkey;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.SessionController.SessionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.webauthn.api.AuthenticatorAssertionResponse;
import org.springframework.security.web.webauthn.api.AuthenticatorAttestationResponse;
import org.springframework.security.web.webauthn.api.PublicKeyCredential;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Passkeys: adding them to your own Account, and signing in with one. See {@link Passkeys}.
 *
 * <p>The options and the browser's answers are in the JSON form of WebAuthn Level 3, which the
 * browser converts with {@code PublicKeyCredential.parseCreationOptionsFromJSON},
 * {@code parseRequestOptionsFromJSON} and {@code toJSON()}. The API description shows them as
 * plain objects; their details are WebAuthn's.
 */
@RestController
@Tag(name = "Passkeys")
class PasskeyController {

    private final Passkeys passkeys;
    private final SignInSessions sessions;
    private final SessionResponses responses;

    PasskeyController(Passkeys passkeys, SignInSessions sessions, SessionResponses responses) {
        this.passkeys = passkeys;
        this.sessions = sessions;
        this.responses = responses;
    }

    @GetMapping("/api/account/passkeys")
    @Operation(summary = "List your own passkeys", description = "The most recently added first.", operationId = "listOwnPasskeys")
    List<PasskeyResponse> list(@AuthenticationPrincipal CurrentAccount account) {
        return passkeys.of(account).stream().map(PasskeyResponse::of).toList();
    }

    @PostMapping("/api/account/passkeys/options")
    @Operation(
            summary = "Start adding a passkey",
            description = "Answers the options for navigator.credentials.create(). Send what it returns to"
                    + " POST /api/account/passkeys. The code passkeys.unavailable means this installation offers no"
                    + " passkeys.",
            operationId = "startAddingPasskey",
            responses = @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(ref = "#/components/schemas/WebAuthnJson"))))
    PublicKeyCredentialCreationOptions startAdding(
            @AuthenticationPrincipal CurrentAccount account, HttpServletRequest request, HttpServletResponse response) {
        return passkeys.startAdding(account, request, response);
    }

    @PostMapping("/api/account/passkeys")
    @Operation(
            summary = "Add a passkey",
            description = "With the browser's answer to the options from POST /api/account/passkeys/options. The"
                    + " options are good for one try.",
            operationId = "addPasskey")
    PasskeyResponse add(
            @AuthenticationPrincipal CurrentAccount account,
            @Valid @RequestBody NewPasskeyRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        return PasskeyResponse.of(passkeys.add(account, body.credential(), body.label(), request, response));
    }

    @DeleteMapping("/api/account/passkeys/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove one of your own passkeys", operationId = "removePasskey")
    void remove(@AuthenticationPrincipal CurrentAccount account, @PathVariable String id) {
        passkeys.remove(account, id);
    }

    @PostMapping("/api/session/passkey/options")
    @Operation(
            summary = "Start signing in with a passkey",
            description = "Open to everyone. Answers the options for navigator.credentials.get(). Send what it"
                    + " returns to POST /api/session/passkey.",
            operationId = "startPasskeySignIn",
            responses = @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(ref = "#/components/schemas/WebAuthnJson"))))
    PublicKeyCredentialRequestOptions startSignIn(HttpServletRequest request, HttpServletResponse response) {
        return passkeys.startSignIn(request, response);
    }

    @PostMapping("/api/session/passkey")
    @Operation(
            summary = "Sign in with a passkey",
            description = "Open to everyone. With the browser's answer to the options from"
                    + " POST /api/session/passkey/options.",
            operationId = "signInWithPasskey")
    SessionResponse signIn(
            @Valid @RequestBody PasskeySignInRequest body, HttpServletRequest request, HttpServletResponse response) {
        UUID accountId = passkeys.signIn(body.credential(), request, response);
        sessions.start(accountId, body.staySignedIn(), request, response);
        return responses.of(accountId);
    }

    @Schema(name = "NewPasskey", requiredProperties = {"label", "credential"})
    record NewPasskeyRequest(
            @Schema(description = "A name to recognise it by, such as \"My phone\"")
                    @NotBlank
                    @Size(max = Passkeys.MAX_LABEL_LENGTH)
                    String label,
            @Schema(ref = "#/components/schemas/WebAuthnJson", description = "What credential.toJSON() returns")
                    @NotNull
                    PublicKeyCredential<AuthenticatorAttestationResponse> credential) {}

    @Schema(name = "PasskeySignIn", requiredProperties = {"credential", "staySignedIn"})
    record PasskeySignInRequest(
            @Schema(ref = "#/components/schemas/WebAuthnJson", description = "What credential.toJSON() returns")
                    @NotNull
                    PublicKeyCredential<AuthenticatorAssertionResponse> credential,
            @Schema(description = "Stay signed in on this device for 90 days after the last visit") boolean staySignedIn) {}

    @Schema(name = "Passkey", requiredProperties = {"id", "label"})
    record PasskeyResponse(
            String id,
            String label,
            @Schema(description = "When it was added") Instant created,
            @Schema(description = "When it was last used to sign in. Absent when it never was.") Instant lastUsed) {

        static PasskeyResponse of(Passkey passkey) {
            return new PasskeyResponse(passkey.id(), passkey.label(), passkey.created(), passkey.lastUsed());
        }
    }
}
