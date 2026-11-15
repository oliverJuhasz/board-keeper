package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import io.github.oliverjuhasz.boardkeeper.access.CurrentAccount;
import io.github.oliverjuhasz.boardkeeper.access.Permission;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.ApiKeyRepository.ApiKeyRow;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.ApiKeyRepository.SecretMatch;
import io.github.oliverjuhasz.boardkeeper.problems.Problem;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.webauthn.api.AuthenticatorAssertionResponse;
import org.springframework.security.web.webauthn.api.PublicKeyCredential;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API Keys: secrets that let a program, such as a script or an AI agent, use the API as one
 * Account without signing in (ADR 0017).
 *
 * <p>An API Key is shown once, when it is created; the database keeps only its hash, as for a
 * Reset Link. A request with an API Key acts as its Account with the Permissions the Account
 * holds at that moment, minus the Admin-only ones. Using API Keys needs
 * {@link Permission#USE_API_KEYS}: without it, the Account's API Keys stop working until it is
 * given back.
 */
@Service
class ApiKeys {

    static final int MAX_NAME_LENGTH = 50;

    /** Every API Key starts with this, so that one is easy to recognise in a file or a log. */
    static final String PREFIX = "bk_";

    /** How often a use is recorded at most. Recording every request would write on every request. */
    static final Duration USE_RECORDED_EVERY = Duration.ofHours(1);

    private static final String NOT_VALID = "access.apiKeyNotValid";

    private final ApiKeyRepository repository;
    private final CurrentAccounts currentAccounts;
    private final Passwords passwords;
    private final Passkeys passkeys;

    ApiKeys(ApiKeyRepository repository, CurrentAccounts currentAccounts, Passwords passwords, Passkeys passkeys) {
        this.repository = repository;
        this.currentAccounts = currentAccounts;
        this.passwords = passwords;
        this.passkeys = passkeys;
    }

    /** How long a new API Key works. */
    enum Expiry {
        THIRTY_DAYS(Duration.ofDays(30)),
        NINETY_DAYS(Duration.ofDays(90)),
        ONE_YEAR(Duration.ofDays(365)),
        NEVER(null);

        private final Duration validity;

        Expiry(Duration validity) {
            this.validity = validity;
        }

        /** When an API Key created at {@code created} stops working, or null if it never does. */
        OffsetDateTime after(OffsetDateTime created) {
            return validity == null ? null : created.plus(validity);
        }
    }

    /** The Account's own API Keys, the most recently created first. */
    @Transactional(readOnly = true)
    List<ApiKeyRow> of(CurrentAccount account) {
        return repository.findByAccount(account.accountId());
    }

    /**
     * Creates an API Key for the signed-in Account. Its owner confirms that it is really them,
     * with either their password or one of their passkeys, because an API Key outlives the
     * session it was made in, and survives a change of password.
     */
    @Transactional
    NewApiKey create(
            CurrentAccount account,
            String name,
            Expiry expiry,
            Optional<String> password,
            Optional<PublicKeyCredential<AuthenticatorAssertionResponse>> passkey,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (password.isPresent() == passkey.isPresent()) {
            throw Problem.unprocessable("apiKeys.confirmationNeeded");
        }
        String trimmed = name.strip();
        // Checked first, so that a taken name doesn't use up what the passkey signed.
        if (repository.nameTaken(account.accountId(), trimmed)) {
            throw Problem.conflict("apiKeys.nameTaken");
        }
        if (password.isPresent()) {
            passwords.confirm(account, password.get());
        } else {
            passkeys.confirm(account, passkey.get(), request, response);
        }

        String secret = PREFIX + LinkTokens.newToken();
        try {
            ApiKeyRow created = repository.insert(
                    account.accountId(), trimmed, LinkTokens.hash(secret), expiry.after(OffsetDateTime.now()));
            return new NewApiKey(created, secret);
        } catch (DuplicateKeyException e) {
            // The same name was taken between the check above and the insert.
            throw Problem.conflict("apiKeys.nameTaken");
        }
    }

    /** Deletes one of the Account's own API Keys. Allowed even without the Permission to use them. */
    @Transactional
    void delete(CurrentAccount account, UUID id) {
        if (!repository.delete(account.accountId(), id)) {
            throw Problem.notFound("apiKeys.notFound");
        }
    }

    /** Deletes every API Key of an Account, as when it is disabled. */
    @Transactional
    void deleteAllOf(UUID accountId) {
        repository.deleteAllOf(accountId);
    }

    /** Who a request with this API Key acts as, or why it is refused. */
    @Transactional
    Check check(String secret) {
        Optional<SecretMatch> match = repository.findBySecretHash(LinkTokens.hash(secret));
        if (match.isEmpty()) {
            return new Check.Refused(HttpStatus.UNAUTHORIZED, NOT_VALID);
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (match.get().expiresAt() != null && !match.get().expiresAt().isAfter(now)) {
            return new Check.Refused(HttpStatus.UNAUTHORIZED, "access.apiKeyExpired");
        }
        // Empty for a disabled Account, whose API Keys are deleted anyway.
        Optional<CurrentAccount> account = currentAccounts.load(match.get().accountId());
        if (account.isEmpty()) {
            return new Check.Refused(HttpStatus.UNAUTHORIZED, NOT_VALID);
        }
        if (!account.get().has(Permission.USE_API_KEYS)) {
            return new Check.Refused(HttpStatus.FORBIDDEN, "access.apiKeysNotAllowed");
        }
        repository.recordUse(match.get().id(), now, USE_RECORDED_EVERY);
        return new Check.Accepted(withoutAdminWork(account.get()));
    }

    /** An API Key never does Admin work, even for an Admin (ADR 0017). */
    private static CurrentAccount withoutAdminWork(CurrentAccount account) {
        return new CurrentAccount(
                account.accountId(),
                account.playerId(),
                account.username(),
                account.permissions().stream()
                        .filter(permission -> !permission.isAdminOnly())
                        .collect(Collectors.toUnmodifiableSet()));
    }

    /** What a request's API Key turned out to be. */
    sealed interface Check {

        /** The request acts as this Account, with the Permissions an API Key may have. */
        record Accepted(CurrentAccount account) implements Check {}

        /** The request is refused with this status and code. */
        record Refused(HttpStatus status, String code) implements Check {}
    }

    /** A just-created API Key, with its secret, which is not stored and cannot be shown again. */
    record NewApiKey(ApiKeyRow apiKey, String secret) {}
}
