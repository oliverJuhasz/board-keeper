package io.github.oliverjuhasz.boardkeeper.access;

import java.util.Set;
import java.util.UUID;

/**
 * The signed-in Account behind the current request, with the Permissions it holds right now.
 * It is read from the database on every request, so a change to an Account's Roles takes
 * effect at once.
 *
 * <p>A controller receives it by declaring a parameter
 * {@code @AuthenticationPrincipal CurrentAccount account}.
 */
public record CurrentAccount(UUID accountId, UUID playerId, String username, Set<Permission> permissions) {

    public boolean has(Permission permission) {
        return permissions.contains(permission);
    }
}
