package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import io.github.oliverjuhasz.boardkeeper.access.CurrentAccount;
import io.github.oliverjuhasz.boardkeeper.access.Permission;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.RoleRepository.RoleRow;
import io.github.oliverjuhasz.boardkeeper.problems.Problem;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creating, changing and deleting Roles.
 *
 * <p>One rule runs through all of it, and through {@link AccountAdministration} too: nobody
 * can hand out more than they hold themselves. Without it, the Permission to manage Roles
 * would be a way to become an Admin: add every Permission to a Role you already have.
 */
@Service
class RoleAdministration {

    static final int MAX_NAME_LENGTH = 50;

    private static final String NOT_FOUND = "roles.notFound";
    private static final String NAME_TAKEN = "roles.nameTaken";

    private final RoleRepository roles;
    private final InviteRepository invites;

    RoleAdministration(RoleRepository roles, InviteRepository invites) {
        this.roles = roles;
        this.invites = invites;
    }

    /** The rule: an Account may only deal in Permissions it holds itself. */
    static void requireWithinOwn(CurrentAccount actor, Set<Permission> permissions) {
        if (!actor.permissions().containsAll(permissions)) {
            throw new Problem(HttpStatus.FORBIDDEN, "access.beyondOwnPermissions");
        }
    }

    @Transactional
    RoleRow create(String name, Set<Permission> permissions, CurrentAccount actor) {
        requireWithinOwn(actor, permissions);
        String trimmed = name.strip();
        if (roles.findIdByName(trimmed).isPresent()) {
            throw Problem.conflict(NAME_TAKEN);
        }
        try {
            UUID id = roles.insert(trimmed);
            roles.setPermissions(id, permissions);
            return new RoleRow(id, trimmed, null, permissions);
        } catch (DuplicateKeyException e) {
            throw Problem.conflict(NAME_TAKEN);
        }
    }

    /**
     * Changes a Role's name and Permissions. The Admin Role cannot be changed at all. The Member
     * Role keeps its name, which the interface translates, but its Permissions can change.
     */
    @Transactional
    RoleRow update(UUID roleId, String name, Set<Permission> permissions, CurrentAccount actor) {
        RoleRow role = roles.find(roleId).orElseThrow(() -> Problem.notFound(NOT_FOUND));
        if (role.builtIn() == BuiltInRole.ADMIN) {
            throw Problem.conflict("roles.adminCannotChange");
        }
        requireWithinOwn(actor, role.permissions());
        requireWithinOwn(actor, permissions);

        String newName = role.builtIn() == null ? name.strip() : role.name();
        if (roles.findIdByName(newName).filter(other -> !other.equals(roleId)).isPresent()) {
            throw Problem.conflict(NAME_TAKEN);
        }
        try {
            roles.rename(roleId, newName);
            roles.setPermissions(roleId, permissions);
        } catch (DuplicateKeyException e) {
            throw Problem.conflict(NAME_TAKEN);
        }
        return new RoleRow(roleId, newName, role.builtIn(), permissions);
    }

    /**
     * Deletes a Role. Accounts that held it simply lose it, and open Invites that would have
     * given it give the Member Role instead. The two built-in Roles cannot be deleted.
     */
    @Transactional
    void delete(UUID roleId, CurrentAccount actor) {
        RoleRow role = roles.find(roleId).orElseThrow(() -> Problem.notFound(NOT_FOUND));
        if (role.builtIn() != null) {
            throw Problem.conflict("roles.builtInCannotDelete");
        }
        requireWithinOwn(actor, role.permissions());
        invites.replaceRole(roleId, roles.idOf(BuiltInRole.MEMBER));
        roles.delete(roleId);
    }
}
