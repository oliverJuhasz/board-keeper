package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.PASSWORD_RESET;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
class PasswordResetRepository {

    private final DSLContext dsl;

    PasswordResetRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    Optional<ResetRow> findByTokenHash(String tokenHash) {
        return dsl.select(PASSWORD_RESET.ID, PASSWORD_RESET.ACCOUNT_ID, PASSWORD_RESET.EXPIRES_AT)
                .from(PASSWORD_RESET)
                .where(PASSWORD_RESET.TOKEN_HASH.eq(tokenHash))
                .fetchOptional(row -> new ResetRow(row.value1(), row.value2(), row.value3()));
    }

    void insert(String tokenHash, UUID accountId, UUID createdBy, OffsetDateTime expiresAt) {
        dsl.insertInto(
                        PASSWORD_RESET,
                        PASSWORD_RESET.TOKEN_HASH,
                        PASSWORD_RESET.ACCOUNT_ID,
                        PASSWORD_RESET.CREATED_BY,
                        PASSWORD_RESET.EXPIRES_AT)
                .values(tokenHash, accountId, createdBy, expiresAt)
                .execute();
    }

    void deleteByAccount(UUID accountId) {
        dsl.deleteFrom(PASSWORD_RESET).where(PASSWORD_RESET.ACCOUNT_ID.eq(accountId)).execute();
    }

    record ResetRow(UUID id, UUID accountId, OffsetDateTime expiresAt) {}
}
