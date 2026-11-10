package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.ACCOUNT;
import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.ACCOUNT_ROLE;
import static org.jooq.impl.DSL.lower;

import io.github.oliverjuhasz.boardkeeper.language.Language;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
class AccountRepository {

    private final DSLContext dsl;

    AccountRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    boolean anyExist() {
        return dsl.fetchExists(ACCOUNT);
    }

    boolean existsByUsername(String username) {
        return dsl.fetchExists(ACCOUNT, lower(ACCOUNT.USERNAME).eq(username.toLowerCase()));
    }

    boolean existsByPlayer(UUID playerId) {
        return dsl.fetchExists(ACCOUNT, ACCOUNT.PLAYER_ID.eq(playerId));
    }

    /** The Players that have an Account. */
    List<UUID> findClaimedPlayerIds() {
        return dsl.select(ACCOUNT.PLAYER_ID).from(ACCOUNT).orderBy(ACCOUNT.PLAYER_ID).fetch(row -> row.value1());
    }

    /** What signing in needs to know, found by username whatever its capitals. */
    Optional<Credentials> findCredentials(String username) {
        return dsl.select(ACCOUNT.ID, ACCOUNT.PASSWORD_HASH, ACCOUNT.DISABLED)
                .from(ACCOUNT)
                .where(lower(ACCOUNT.USERNAME).eq(username.toLowerCase()))
                .fetchOptional(row -> new Credentials(row.value1(), row.value2(), row.value3()));
    }

    Optional<AccountRow> find(UUID id) {
        return dsl.select(ACCOUNT.ID, ACCOUNT.USERNAME, ACCOUNT.PLAYER_ID, ACCOUNT.LANGUAGE, ACCOUNT.DISABLED)
                .from(ACCOUNT)
                .where(ACCOUNT.ID.eq(id))
                .fetchOptional(row -> new AccountRow(
                        row.value1(),
                        row.value2(),
                        row.value3(),
                        Language.fromCode(row.value4()).orElse(Language.FALLBACK),
                        row.value5()));
    }

    UUID insert(String username, String passwordHash, UUID playerId, Language language) {
        return dsl.insertInto(ACCOUNT, ACCOUNT.USERNAME, ACCOUNT.PASSWORD_HASH, ACCOUNT.PLAYER_ID, ACCOUNT.LANGUAGE)
                .values(username, passwordHash, playerId, language.code())
                .returningResult(ACCOUNT.ID)
                .fetchSingle()
                .value1();
    }

    void addRole(UUID accountId, UUID roleId) {
        dsl.insertInto(ACCOUNT_ROLE, ACCOUNT_ROLE.ACCOUNT_ID, ACCOUNT_ROLE.ROLE_ID)
                .values(accountId, roleId)
                .execute();
    }

    /** Every Account with the ids of its Roles, ordered by username. */
    List<AccountSummary> findAllWithRoles() {
        Map<UUID, List<UUID>> roles = dsl.select(ACCOUNT_ROLE.ACCOUNT_ID, ACCOUNT_ROLE.ROLE_ID)
                .from(ACCOUNT_ROLE)
                .orderBy(ACCOUNT_ROLE.ROLE_ID)
                .fetchGroups(ACCOUNT_ROLE.ACCOUNT_ID, ACCOUNT_ROLE.ROLE_ID);
        return dsl.select(ACCOUNT.ID, ACCOUNT.USERNAME, ACCOUNT.PLAYER_ID, ACCOUNT.DISABLED)
                .from(ACCOUNT)
                .orderBy(lower(ACCOUNT.USERNAME))
                .fetch(row -> new AccountSummary(
                        row.value1(), row.value2(), row.value3(), roles.getOrDefault(row.value1(), List.of()), row.value4()));
    }

    Optional<UUID> findIdByPlayer(UUID playerId) {
        return dsl.select(ACCOUNT.ID).from(ACCOUNT).where(ACCOUNT.PLAYER_ID.eq(playerId)).fetchOptional(row -> row.value1());
    }

    Set<UUID> findRoleIds(UUID accountId) {
        return dsl.select(ACCOUNT_ROLE.ROLE_ID)
                .from(ACCOUNT_ROLE)
                .where(ACCOUNT_ROLE.ACCOUNT_ID.eq(accountId))
                .fetchSet(ACCOUNT_ROLE.ROLE_ID);
    }

    void setRoles(UUID accountId, Set<UUID> roleIds) {
        dsl.deleteFrom(ACCOUNT_ROLE).where(ACCOUNT_ROLE.ACCOUNT_ID.eq(accountId)).execute();
        roleIds.forEach(roleId -> addRole(accountId, roleId));
    }

    void setDisabled(UUID accountId, boolean disabled) {
        dsl.update(ACCOUNT).set(ACCOUNT.DISABLED, disabled).where(ACCOUNT.ID.eq(accountId)).execute();
    }

    /** How many Accounts can sign in and hold the given Role, not counting one of them. */
    int countEnabledWithRole(UUID roleId, UUID exceptAccountId) {
        return dsl.fetchCount(
                ACCOUNT.join(ACCOUNT_ROLE).on(ACCOUNT_ROLE.ACCOUNT_ID.eq(ACCOUNT.ID)),
                ACCOUNT_ROLE.ROLE_ID.eq(roleId).and(ACCOUNT.DISABLED.isFalse()).and(ACCOUNT.ID.ne(exceptAccountId)));
    }

    String findPasswordHash(UUID accountId) {
        return dsl.select(ACCOUNT.PASSWORD_HASH).from(ACCOUNT).where(ACCOUNT.ID.eq(accountId)).fetchSingle().value1();
    }

    void setPasswordHash(UUID accountId, String passwordHash) {
        dsl.update(ACCOUNT).set(ACCOUNT.PASSWORD_HASH, passwordHash).where(ACCOUNT.ID.eq(accountId)).execute();
    }

    void setLanguage(UUID accountId, Language language) {
        dsl.update(ACCOUNT)
                .set(ACCOUNT.LANGUAGE, language.code())
                .where(ACCOUNT.ID.eq(accountId))
                .execute();
    }

    record Credentials(UUID accountId, String passwordHash, boolean disabled) {}

    record AccountSummary(UUID id, String username, UUID playerId, List<UUID> roleIds, boolean disabled) {}

    record AccountRow(UUID id, String username, UUID playerId, Language language, boolean disabled) {}
}
