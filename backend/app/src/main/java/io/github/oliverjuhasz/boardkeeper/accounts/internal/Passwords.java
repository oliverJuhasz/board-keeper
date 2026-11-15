package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import io.github.oliverjuhasz.boardkeeper.access.CurrentAccount;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.AccountRepository.AccountRow;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.PasswordResetRepository.ResetRow;
import io.github.oliverjuhasz.boardkeeper.problems.Problem;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Changing passwords: your own, when you know the current one, and anyone's through a Reset
 * Link. Board Keeper sends no email, so a forgotten password is solved by an Admin creating a
 * Reset Link and passing it on.
 */
@Service
class Passwords {

    static final Duration RESET_LINK_VALIDITY = Duration.ofHours(24);

    private static final String NOT_VALID = "resetLinks.notValid";

    private final AccountRepository accounts;
    private final PasswordResetRepository resets;
    private final PasswordEncoder passwordEncoder;
    private final SignInSessions sessions;

    Passwords(
            AccountRepository accounts,
            PasswordResetRepository resets,
            PasswordEncoder passwordEncoder,
            SignInSessions sessions) {
        this.accounts = accounts;
        this.resets = resets;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
    }

    /** Changes the signed-in Account's password, and signs it out on every other device. */
    @Transactional
    void changeOwn(CurrentAccount account, String currentPassword, String newPassword, HttpServletRequest request) {
        confirm(account, currentPassword);
        accounts.setPasswordHash(account.accountId(), passwordEncoder.encode(newPassword));
        sessions.endAllOf(account.accountId(), request);
    }

    /**
     * Checks that the signed-in Account's owner knows its password, before something that needs
     * more than a session: changing the password, or creating an API Key.
     */
    @Transactional(readOnly = true)
    void confirm(CurrentAccount account, String password) {
        if (!passwordEncoder.matches(password, accounts.findPasswordHash(account.accountId()))) {
            throw new Problem(HttpStatus.FORBIDDEN, "accounts.wrongCurrentPassword");
        }
    }

    /**
     * Creates a Reset Link for the Account of a Player, replacing any earlier one. A Reset Link
     * is the key to an Account, which is why only an Admin can create one.
     */
    @Transactional
    NewResetLink createResetLink(UUID playerId, CurrentAccount creator) {
        UUID accountId = accounts.findIdByPlayer(playerId).orElseThrow(() -> Problem.notFound("accounts.notFound"));
        return newResetLink(accountId, creator.accountId());
    }

    /**
     * Creates a Reset Link for an Account from the command line, where no Admin creates it:
     * see {@link ResetLinkCommand}.
     */
    @Transactional
    NewResetLink createResetLinkFromCommandLine(UUID accountId) {
        return newResetLink(accountId, null);
    }

    /** @param createdBy the Admin's Account, or null when the link was made on the command line */
    private NewResetLink newResetLink(UUID accountId, UUID createdBy) {
        String token = LinkTokens.newToken();
        OffsetDateTime expiresAt = OffsetDateTime.now().plus(RESET_LINK_VALIDITY);
        resets.deleteByAccount(accountId);
        resets.insert(LinkTokens.hash(token), accountId, createdBy, expiresAt);
        return new NewResetLink(token, expiresAt);
    }

    /** The username a Reset Link is for. Anything wrong with the link gives the same Problem. */
    @Transactional(readOnly = true)
    String open(String token) {
        return accountOf(valid(token)).username();
    }

    /** Sets the new password, uses the link up, and signs the Account out everywhere. */
    @Transactional
    UUID reset(String token, String newPassword, HttpServletRequest request) {
        ResetRow reset = valid(token);
        AccountRow account = accountOf(reset);
        accounts.setPasswordHash(account.id(), passwordEncoder.encode(newPassword));
        resets.deleteByAccount(account.id());
        sessions.endAllOf(account.id(), request);
        return account.id();
    }

    private ResetRow valid(String token) {
        return resets.findByTokenHash(LinkTokens.hash(token))
                .filter(reset -> reset.expiresAt().isAfter(OffsetDateTime.now()))
                .orElseThrow(() -> Problem.notFound(NOT_VALID));
    }

    /** A disabled Account cannot be opened with a Reset Link either. */
    private AccountRow accountOf(ResetRow reset) {
        return accounts.find(reset.accountId())
                .filter(account -> !account.disabled())
                .orElseThrow(() -> Problem.notFound(NOT_VALID));
    }

    /** A just-created Reset Link with its token, which is not stored and cannot be shown again. */
    record NewResetLink(String token, OffsetDateTime expiresAt) {}
}
