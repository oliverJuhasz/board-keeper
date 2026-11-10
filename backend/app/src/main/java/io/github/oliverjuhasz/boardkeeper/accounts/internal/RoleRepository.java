package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.ACCOUNT_ROLE;
import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.ROLE;
import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.ROLE_PERMISSION;

import io.github.oliverjuhasz.boardkeeper.access.Permission;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
class RoleRepository {

    private final DSLContext dsl;

    RoleRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    UUID idOf(BuiltInRole role) {
        return dsl.select(ROLE.ID)
                .from(ROLE)
                .where(ROLE.BUILT_IN.eq(role.name()))
                .fetchSingle()
                .value1();
    }

    /** Everything the Account may do: the Permissions of all its Roles together. */
    Set<Permission> permissionsOf(UUID accountId) {
        var rows = dsl.select(ROLE.BUILT_IN, ROLE_PERMISSION.PERMISSION)
                .from(ACCOUNT_ROLE)
                .join(ROLE)
                .on(ROLE.ID.eq(ACCOUNT_ROLE.ROLE_ID))
                .leftJoin(ROLE_PERMISSION)
                .on(ROLE_PERMISSION.ROLE_ID.eq(ROLE.ID))
                .where(ACCOUNT_ROLE.ACCOUNT_ID.eq(accountId))
                .fetch();

        Set<Permission> permissions = EnumSet.noneOf(Permission.class);
        for (var row : rows) {
            if (BuiltInRole.ADMIN.name().equals(row.value1())) {
                return EnumSet.allOf(Permission.class);
            }
            parse(row.value2()).ifPresent(permissions::add);
        }
        return permissions;
    }

    /** A name in the database that the code no longer knows is skipped, not an error. */
    private static java.util.Optional<Permission> parse(String name) {
        if (name == null) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(Permission.valueOf(name));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }
}
