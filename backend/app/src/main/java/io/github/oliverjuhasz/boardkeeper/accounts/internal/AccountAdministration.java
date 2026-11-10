package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import io.github.oliverjuhasz.boardkeeper.access.CurrentAccount;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.AccountRepository.AccountRow;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.AccountRepository.AccountSummary;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.RoleRepository.RoleRow;
import io.github.oliverjuhasz.boardkeeper.problems.Problem;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What someone who may manage Accounts can do to them: change their Roles, and disable or
 * enable them. Two things are protected throughout:
 *
 * <ul>
 *   <li>Nobody can hand out, take away or overrule more than they hold themselves (see
 *       {@link RoleAdministration#requireWithinOwn}).
 *   <li>There is always at least one Admin who can sign in.
 * </ul>
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
    void setRoles(UUID accountId, Set<UUID> roleIds, CurrentAccount actor) {
        accounts.find(accountId).orElseThrow(() -> Problem.notFound(NOT_FOUND));
        Set<UUID> before = accounts.findRoleIds(accountId);

        // Every Role that is given or taken away has to be within what the actor holds.
        Set<UUID> changed = new HashSet<>(before);
        changed.addAll(roleIds);
        changed.removeIf(roleId -> before.contains(roleId) && roleIds.contains(roleId));
        for (UUID roleId : changed) {
            RoleRow role = roles.find(roleId).orElseThrow(() -> Problem.notFound("roles.notFound"));
            RoleAdministration.requireWithinOwn(actor, role.permissions());
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
        RoleAdministration.requireWithinOwn(actor, roles.permissionsOf(accountId));
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
