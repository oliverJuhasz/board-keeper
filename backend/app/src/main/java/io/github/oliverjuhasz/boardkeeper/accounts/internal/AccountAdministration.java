package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import io.github.oliverjuhasz.boardkeeper.access.CurrentAccount;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.AccountRepository.AccountRow;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.AccountRepository.AccountSummary;
import io.github.oliverjuhasz.boardkeeper.problems.Problem;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What an Admin can do to other people's Accounts: change their Roles, and disable or enable
 * them. One thing is protected throughout: there is always at least one Admin who can sign in.
 */
@Service
class AccountAdministration {

    private static final String NOT_FOUND = "accounts.notFound";

    private final AccountRepository accounts;
    private final RoleRepository roles;
    private final SignInSessions sessions;

    AccountAdministration(AccountRepository accounts, RoleRepository roles, SignInSessions sessions) {
        this.accounts = accounts;
        this.roles = roles;
        this.sessions = sessions;
    }

    List<AccountSummary> all() {
        return accounts.findAllWithRoles();
    }

    @Transactional
    void setRoles(UUID accountId, Set<UUID> roleIds) {
        accounts.find(accountId).orElseThrow(() -> Problem.notFound(NOT_FOUND));
        Set<UUID> before = accounts.findRoleIds(accountId);
        for (UUID roleId : roleIds) {
            if (!roles.exists(roleId)) {
                throw Problem.notFound("roles.notFound");
            }
        }

        UUID admin = roles.idOf(BuiltInRole.ADMIN);
        if (before.contains(admin) && !roleIds.contains(admin)) {
            requireAnotherAdmin(accountId);
        }
        accounts.setRoles(accountId, roleIds);
    }

    @Transactional
    void setDisabled(UUID accountId, boolean disabled, CurrentAccount actor, HttpServletRequest request) {
        AccountRow account = accounts.find(accountId).orElseThrow(() -> Problem.notFound(NOT_FOUND));
        if (accountId.equals(actor.accountId())) {
            throw Problem.conflict("accounts.cannotDisableSelf");
        }
        if (disabled && !account.disabled() && accounts.findRoleIds(accountId).contains(roles.idOf(BuiltInRole.ADMIN))) {
            requireAnotherAdmin(accountId);
        }
        accounts.setDisabled(accountId, disabled);
        if (disabled) {
            sessions.endAllOf(accountId, request);
        }
    }

    /** Refuses a change that would leave no Admin who can sign in. */
    private void requireAnotherAdmin(UUID exceptAccountId) {
        if (accounts.countEnabledWithRole(roles.idOf(BuiltInRole.ADMIN), exceptAccountId) == 0) {
            throw new Problem(HttpStatus.CONFLICT, "accounts.lastAdmin");
        }
    }
}
