package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.INVITE;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
class InviteRepository {

    private final DSLContext dsl;

    InviteRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    List<InviteRow> findAll() {
        return dsl.select(INVITE.ID, INVITE.PLAYER_ID, INVITE.ROLE_ID, INVITE.EXPIRES_AT)
                .from(INVITE)
                .orderBy(INVITE.CREATED_AT, INVITE.ID)
                .fetch(row -> new InviteRow(row.value1(), row.value2(), row.value3(), row.value4()));
    }

    Optional<InviteRow> findByTokenHash(String tokenHash) {
        return dsl.select(INVITE.ID, INVITE.PLAYER_ID, INVITE.ROLE_ID, INVITE.EXPIRES_AT)
                .from(INVITE)
                .where(INVITE.TOKEN_HASH.eq(tokenHash))
                .fetchOptional(row -> new InviteRow(row.value1(), row.value2(), row.value3(), row.value4()));
    }

    InviteRow insert(String tokenHash, UUID playerId, UUID roleId, UUID createdBy, OffsetDateTime expiresAt) {
        UUID id = dsl.insertInto(
                        INVITE, INVITE.TOKEN_HASH, INVITE.PLAYER_ID, INVITE.ROLE_ID, INVITE.CREATED_BY, INVITE.EXPIRES_AT)
                .values(tokenHash, playerId, roleId, createdBy, expiresAt)
                .returningResult(INVITE.ID)
                .fetchSingle()
                .value1();
        return new InviteRow(id, playerId, roleId, expiresAt);
    }

    /** Points every Invite that would give one Role at another one instead. */
    void replaceRole(UUID roleId, UUID replacementRoleId) {
        dsl.update(INVITE).set(INVITE.ROLE_ID, replacementRoleId).where(INVITE.ROLE_ID.eq(roleId)).execute();
    }

    void deleteByPlayer(UUID playerId) {
        dsl.deleteFrom(INVITE).where(INVITE.PLAYER_ID.eq(playerId)).execute();
    }

    /** False when there is no such Invite. */
    boolean delete(UUID id) {
        return dsl.deleteFrom(INVITE).where(INVITE.ID.eq(id)).execute() == 1;
    }

    record InviteRow(UUID id, UUID playerId, UUID roleId, OffsetDateTime expiresAt) {

        boolean isExpiredAt(OffsetDateTime moment) {
            return !expiresAt.isAfter(moment);
        }
    }
}
