package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.ACCOUNT;
import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.ACCOUNT_ROLE;
import static org.jooq.impl.DSL.lower;

import io.github.oliverjuhasz.boardkeeper.language.Language;
import java.util.List;
import java.util.Optional;
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

    void setLanguage(UUID accountId, Language language) {
        dsl.update(ACCOUNT)
                .set(ACCOUNT.LANGUAGE, language.code())
                .where(ACCOUNT.ID.eq(accountId))
                .execute();
    }

    record Credentials(UUID accountId, String passwordHash, boolean disabled) {}

    record AccountRow(UUID id, String username, UUID playerId, Language language, boolean disabled) {}
}
