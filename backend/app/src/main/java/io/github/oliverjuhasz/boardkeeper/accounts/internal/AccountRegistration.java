package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import io.github.oliverjuhasz.boardkeeper.language.Language;
import io.github.oliverjuhasz.boardkeeper.problems.Problem;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates Accounts. Every Account claims exactly one Player and starts with one Role. */
@Service
class AccountRegistration {

    private static final String USERNAME_TAKEN = "accounts.usernameTaken";

    private final AccountRepository accounts;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;

    AccountRegistration(AccountRepository accounts, RoleRepository roles, PasswordEncoder passwordEncoder) {
        this.accounts = accounts;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    UUID register(String username, String password, UUID playerId, Language language, BuiltInRole role) {
        return register(username, password, playerId, language, roles.idOf(role));
    }

    @Transactional
    UUID register(String username, String password, UUID playerId, Language language, UUID roleId) {
        if (accounts.existsByUsername(username)) {
            throw Problem.conflict(USERNAME_TAKEN);
        }
        try {
            // Only a hash of the password is stored, never the password itself.
            UUID accountId = accounts.insert(username, passwordEncoder.encode(password), playerId, language);
            accounts.addRole(accountId, roleId);
            return accountId;
        } catch (DuplicateKeyException e) {
            throw Problem.conflict(USERNAME_TAKEN);
        }
    }
}
