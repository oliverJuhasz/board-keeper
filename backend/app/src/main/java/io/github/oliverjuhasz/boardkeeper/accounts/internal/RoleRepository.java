package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.ACCOUNT_ROLE;
import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.ROLE;
import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.ROLE_PERMISSION;
import static org.jooq.impl.DSL.lower;

import io.github.oliverjuhasz.boardkeeper.access.Permission;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    boolean exists(UUID roleId) {
        return dsl.fetchExists(ROLE, ROLE.ID.eq(roleId));
    }

    /** Every Role with its Permissions: the built-in ones first, then the others by name. */
    List<RoleRow> findAll() {
        Map<UUID, Set<Permission>> permissions = new HashMap<>();
        dsl.select(ROLE_PERMISSION.ROLE_ID, ROLE_PERMISSION.PERMISSION)
                .from(ROLE_PERMISSION)
                .fetch()
                .forEach(row -> parse(row.value2()).ifPresent(permission -> permissions
                        .computeIfAbsent(row.value1(), id -> EnumSet.noneOf(Permission.class))
                        .add(permission)));

        return dsl.select(ROLE.ID, ROLE.NAME, ROLE.BUILT_IN)
                .from(ROLE)
                .orderBy(ROLE.BUILT_IN.asc().nullsLast(), lower(ROLE.NAME))
                .fetch(row -> {
                    BuiltInRole builtIn = row.value3() == null ? null : BuiltInRole.valueOf(row.value3());
                    Set<Permission> held = builtIn == BuiltInRole.ADMIN
                            ? EnumSet.allOf(Permission.class)
                            : permissions.getOrDefault(row.value1(), EnumSet.noneOf(Permission.class));
                    return new RoleRow(row.value1(), row.value2(), builtIn, held);
                });
    }

    Optional<RoleRow> find(UUID roleId) {
        return findAll().stream().filter(role -> role.id().equals(roleId)).findFirst();
    }

    /** The Role with this name, whatever its capitals. */
    Optional<UUID> findIdByName(String name) {
        return dsl.select(ROLE.ID)
                .from(ROLE)
                .where(lower(ROLE.NAME).eq(name.toLowerCase()))
                .fetchOptional(row -> row.value1());
    }

    UUID insert(String name) {
        return dsl.insertInto(ROLE, ROLE.NAME).values(name).returningResult(ROLE.ID).fetchSingle().value1();
    }

    void rename(UUID roleId, String name) {
        dsl.update(ROLE).set(ROLE.NAME, name).where(ROLE.ID.eq(roleId)).execute();
    }

    void setPermissions(UUID roleId, Set<Permission> permissions) {
        dsl.deleteFrom(ROLE_PERMISSION).where(ROLE_PERMISSION.ROLE_ID.eq(roleId)).execute();
        for (Permission permission : permissions) {
            dsl.insertInto(ROLE_PERMISSION, ROLE_PERMISSION.ROLE_ID, ROLE_PERMISSION.PERMISSION)
                    .values(roleId, permission.name())
                    .execute();
        }
    }

    /** Deleting a Role also takes it away from every Account that held it. */
    void delete(UUID roleId) {
        dsl.deleteFrom(ROLE).where(ROLE.ID.eq(roleId)).execute();
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

    /** A Role as stored. {@code builtIn} is null for a Role that an Admin created. */
    record RoleRow(UUID id, String name, BuiltInRole builtIn, Set<Permission> permissions) {}

    /** A name in the database that the code no longer knows is skipped, not an error. */
    private static Optional<Permission> parse(String name) {
        if (name == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Permission.valueOf(name));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
