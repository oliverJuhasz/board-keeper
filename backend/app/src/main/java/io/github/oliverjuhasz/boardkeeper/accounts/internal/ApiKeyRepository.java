package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.API_KEY;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record5;
import org.jooq.SelectSelectStep;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
class ApiKeyRepository {

    private final DSLContext dsl;

    ApiKeyRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    ApiKeyRow insert(UUID accountId, String name, String secretHash, OffsetDateTime expiresAt) {
        return dsl.insertInto(API_KEY, API_KEY.ACCOUNT_ID, API_KEY.NAME, API_KEY.SECRET_HASH, API_KEY.EXPIRES_AT)
                .values(accountId, name, secretHash, expiresAt)
                .returningResult(API_KEY.ID, API_KEY.NAME, API_KEY.CREATED_AT, API_KEY.EXPIRES_AT, API_KEY.LAST_USED_AT)
                .fetchSingle(ApiKeyRepository::toRow);
    }

    /** One Account's API Keys, the most recently created first. */
    List<ApiKeyRow> findByAccount(UUID accountId) {
        return selectRows()
                .from(API_KEY)
                .where(API_KEY.ACCOUNT_ID.eq(accountId))
                .orderBy(API_KEY.CREATED_AT.desc(), API_KEY.ID.desc())
                .fetch(ApiKeyRepository::toRow);
    }

    boolean nameTaken(UUID accountId, String name) {
        return dsl.fetchExists(
                API_KEY, API_KEY.ACCOUNT_ID.eq(accountId).and(DSL.lower(API_KEY.NAME).eq(DSL.lower(DSL.val(name)))));
    }

    Optional<SecretMatch> findBySecretHash(String secretHash) {
        return dsl.select(API_KEY.ID, API_KEY.ACCOUNT_ID, API_KEY.EXPIRES_AT)
                .from(API_KEY)
                .where(API_KEY.SECRET_HASH.eq(secretHash))
                .fetchOptional(row -> new SecretMatch(row.value1(), row.value2(), row.value3()));
    }

    /** Records a use, unless one was recorded less than {@code interval} ago. */
    void recordUse(UUID id, OffsetDateTime now, Duration interval) {
        dsl.update(API_KEY)
                .set(API_KEY.LAST_USED_AT, now)
                .where(API_KEY.ID.eq(id))
                .and(API_KEY.LAST_USED_AT.isNull().or(API_KEY.LAST_USED_AT.lt(now.minus(interval))))
                .execute();
    }

    /** @return whether the Account had an API Key with this id */
    boolean delete(UUID accountId, UUID id) {
        return dsl.deleteFrom(API_KEY)
                        .where(API_KEY.ID.eq(id))
                        .and(API_KEY.ACCOUNT_ID.eq(accountId))
                        .execute()
                > 0;
    }

    void deleteAllOf(UUID accountId) {
        dsl.deleteFrom(API_KEY).where(API_KEY.ACCOUNT_ID.eq(accountId)).execute();
    }

    private SelectSelectStep<Record5<UUID, String, OffsetDateTime, OffsetDateTime, OffsetDateTime>> selectRows() {
        return dsl.select(API_KEY.ID, API_KEY.NAME, API_KEY.CREATED_AT, API_KEY.EXPIRES_AT, API_KEY.LAST_USED_AT);
    }

    private static ApiKeyRow toRow(Record5<UUID, String, OffsetDateTime, OffsetDateTime, OffsetDateTime> row) {
        return new ApiKeyRow(row.value1(), row.value2(), row.value3(), row.value4(), row.value5());
    }

    /** An API Key as its owner sees it. The expiry and last use are null when there is none. */
    record ApiKeyRow(UUID id, String name, OffsetDateTime createdAt, OffsetDateTime expiresAt, OffsetDateTime lastUsedAt) {}

    /** The API Key that a secret belongs to. */
    record SecretMatch(UUID id, UUID accountId, OffsetDateTime expiresAt) {}
}
