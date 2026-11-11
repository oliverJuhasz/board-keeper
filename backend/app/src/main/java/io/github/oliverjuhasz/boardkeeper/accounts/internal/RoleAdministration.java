package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import io.github.oliverjuhasz.boardkeeper.access.Permission;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.RoleRepository.RoleRow;
import io.github.oliverjuhasz.boardkeeper.problems.Problem;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creating, changing and deleting Roles. Only an Admin can do this, so there is no question of
 * someone handing out more than they hold. What a Role can hold is limited instead: the
 * Admin-only Permissions belong to the Admin Role alone (ADR 0015).
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

    /** Refuses a Role that would hold a Permission meant for Admins only. */
    private static void requireNoAdminOnly(Set<Permission> permissions) {
        if (permissions.stream().anyMatch(Permission::isAdminOnly)) {
            throw Problem.conflict("roles.adminOnlyPermission");
        }
    }

    @Transactional
    RoleRow create(String name, Set<Permission> permissions) {
        requireNoAdminOnly(permissions);
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
    RoleRow update(UUID roleId, String name, Set<Permission> permissions) {
        RoleRow role = roles.find(roleId).orElseThrow(() -> Problem.notFound(NOT_FOUND));
        if (role.builtIn() == BuiltInRole.ADMIN) {
            throw Problem.conflict("roles.adminCannotChange");
        }
        requireNoAdminOnly(permissions);

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
    void delete(UUID roleId) {
        RoleRow role = roles.find(roleId).orElseThrow(() -> Problem.notFound(NOT_FOUND));
        if (role.builtIn() != null) {
            throw Problem.conflict("roles.builtInCannotDelete");
        }
        invites.replaceRole(roleId, roles.idOf(BuiltInRole.MEMBER));
        roles.delete(roleId);
    }
}
