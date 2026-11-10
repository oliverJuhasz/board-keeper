package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import io.github.oliverjuhasz.boardkeeper.access.CurrentAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loads an Account as the rest of the application sees the one that is signed in. */
@Service
class CurrentAccounts {

    private final AccountRepository accounts;
    private final RoleRepository roles;

    CurrentAccounts(AccountRepository accounts, RoleRepository roles) {
        this.accounts = accounts;
        this.roles = roles;
    }

    /** Empty when the Account no longer exists or has been disabled. */
    @Transactional(readOnly = true)
    Optional<CurrentAccount> load(UUID accountId) {
        return accounts.find(accountId)
                .filter(account -> !account.disabled())
                .map(account -> new CurrentAccount(
                        account.id(), account.playerId(), account.username(), roles.permissionsOf(account.id())));
    }
}
