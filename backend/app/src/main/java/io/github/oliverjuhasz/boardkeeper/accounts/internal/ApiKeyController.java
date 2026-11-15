package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import io.github.oliverjuhasz.boardkeeper.access.CurrentAccount;
import io.github.oliverjuhasz.boardkeeper.access.Permission;
import io.github.oliverjuhasz.boardkeeper.access.RequiresPermission;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.ApiKeyRepository.ApiKeyRow;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.ApiKeys.Expiry;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.ApiKeys.NewApiKey;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.webauthn.api.AuthenticatorAssertionResponse;
import org.springframework.security.web.webauthn.api.PublicKeyCredential;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in Account's own API Keys. See {@link ApiKeys}.
 *
 * <p>These need a session: a request with an API Key can't see or change API Keys
 * (SecurityConfiguration).
 */
@RestController
@RequestMapping("/api/account/api-keys")
@Tag(name = "API Keys")
class ApiKeyController {

    private final ApiKeys apiKeys;
    private final Passkeys passkeys;

    ApiKeyController(ApiKeys apiKeys, Passkeys passkeys) {
        this.apiKeys = apiKeys;
        this.passkeys = passkeys;
    }

    @GetMapping
    @Operation(
            summary = "List your own API Keys",
            description = "The most recently created first. Listed even without the Permission to use them.",
            operationId = "listOwnApiKeys")
    List<ApiKeyResponse> list(@AuthenticationPrincipal CurrentAccount account) {
        return apiKeys.of(account).stream().map(ApiKeyResponse::of).toList();
    }

    @PostMapping("/passkey-options")
    @RequiresPermission(Permission.USE_API_KEYS)
    @Operation(
            summary = "Start confirming a new API Key with a passkey",
            description = "Answers the options for navigator.credentials.get(), naming your own passkeys. Send"
                    + " what it returns as the passkey of POST /api/account/api-keys. The code"
                    + " passkeys.unavailable means this installation offers no passkeys.",
            operationId = "startConfirmingApiKey",
            responses = @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(ref = "#/components/schemas/WebAuthnJson"))))
    PublicKeyCredentialRequestOptions startConfirming(
            @AuthenticationPrincipal CurrentAccount account, HttpServletRequest request, HttpServletResponse response) {
        return passkeys.startConfirming(account, request, response);
    }

    @PostMapping
    @RequiresPermission(Permission.USE_API_KEYS)
    @Operation(
            summary = "Create an API Key",
            description = "Confirmed with either your password or one of your passkeys. The answer holds the"
                    + " API Key's secret, which is not stored and cannot be shown again.",
            operationId = "createApiKey")
    NewApiKeyResponse create(
            @AuthenticationPrincipal CurrentAccount account,
            @Valid @RequestBody NewApiKeyRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        NewApiKey created = apiKeys.create(
                account,
                body.name(),
                body.expiry(),
                Optional.ofNullable(body.password()),
                Optional.ofNullable(body.passkey()),
                request,
                response);
        return new NewApiKeyResponse(ApiKeyResponse.of(created.apiKey()), created.secret());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Delete one of your own API Keys",
            description = "Possible even without the Permission to use them.",
            operationId = "deleteApiKey")
    void delete(@AuthenticationPrincipal CurrentAccount account, @PathVariable UUID id) {
        apiKeys.delete(account, id);
    }

    @Schema(name = "NewApiKey", requiredProperties = {"name", "expiry"})
    record NewApiKeyRequest(
            @Schema(description = "A name to recognise it by, unique among your API Keys, such as \"Backup script\"")
                    @NotBlank
                    @Size(max = ApiKeys.MAX_NAME_LENGTH)
                    String name,
            @Schema(description = "How long it works") @NotNull Expiry expiry,
            @Schema(description = "Your password. Send either this or a passkey.") String password,
            @Schema(
                            ref = "#/components/schemas/WebAuthnJson",
                            description = "What credential.toJSON() returns for the options from"
                                    + " POST /api/account/api-keys/passkey-options. Send either this or your password.")
                    PublicKeyCredential<AuthenticatorAssertionResponse> passkey) {}

    @Schema(name = "ApiKey", requiredProperties = {"id", "name", "created"})
    record ApiKeyResponse(
            UUID id,
            String name,
            @Schema(description = "When it was created") OffsetDateTime created,
            @Schema(description = "When it stops working. Absent when it never does.") OffsetDateTime expires,
            @Schema(description = "When it was last used, recorded at most once an hour. Absent when it never was.")
                    OffsetDateTime lastUsed) {

        static ApiKeyResponse of(ApiKeyRow row) {
            return new ApiKeyResponse(row.id(), row.name(), row.createdAt(), row.expiresAt(), row.lastUsedAt());
        }
    }

    @Schema(name = "CreatedApiKey", requiredProperties = {"apiKey", "secret"})
    record NewApiKeyResponse(
            ApiKeyResponse apiKey,
            @Schema(description = "The API Key itself, starting with bk_. Send it as \"Authorization: Bearer …\".")
                    String secret) {}
}
