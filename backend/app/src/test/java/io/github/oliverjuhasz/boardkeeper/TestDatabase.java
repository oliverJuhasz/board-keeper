package io.github.oliverjuhasz.boardkeeper;

import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.ACCOUNT;
import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.GAME;
import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.INVITE;
import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.PLAY;
import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.PLAYER;
import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.ROLE;
import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.ROLE_PERMISSION;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * Empties the database between tests that cannot simply roll back, because they make several
 * requests that each commit. The two built-in Roles stay, as the migrations created them.
 */
@Component
public class TestDatabase {

    private final DSLContext dsl;

    TestDatabase(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void empty() {
        dsl.deleteFrom(DSL.table("spring_session")).execute();
        // A Play's Results go with it.
        dsl.deleteFrom(PLAY).execute();
        dsl.deleteFrom(INVITE).execute();
        dsl.deleteFrom(ACCOUNT).execute();
        dsl.deleteFrom(PLAYER).execute();
        dsl.deleteFrom(ROLE).where(ROLE.BUILT_IN.isNull()).execute();
        // Put the Member Role back to the Permissions it starts with, in case a test changed them.
        var member = dsl.select(ROLE.ID).from(ROLE).where(ROLE.BUILT_IN.eq("MEMBER")).fetchSingle().value1();
        dsl.deleteFrom(ROLE_PERMISSION).where(ROLE_PERMISSION.ROLE_ID.eq(member)).execute();
        for (String permission : new String[] {
            "CREATE_PLAYERS",
            "CREATE_GAMES",
            "EDIT_GAMES",
            "CREATE_PLAYS",
            "CHANGE_PLAYS_RECORDED",
            "CHANGE_PLAYS_TAKEN_PART_IN"
        }) {
            dsl.insertInto(ROLE_PERMISSION, ROLE_PERMISSION.ROLE_ID, ROLE_PERMISSION.PERMISSION)
                    .values(member, permission)
                    .execute();
        }
        dsl.deleteFrom(GAME).execute();
    }
}
