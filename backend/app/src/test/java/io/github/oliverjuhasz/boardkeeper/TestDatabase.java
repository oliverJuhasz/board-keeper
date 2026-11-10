package io.github.oliverjuhasz.boardkeeper;

import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.ACCOUNT;
import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.GAME;
import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.PLAYER;
import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.ROLE;

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
        dsl.deleteFrom(ACCOUNT).execute();
        dsl.deleteFrom(PLAYER).execute();
        dsl.deleteFrom(ROLE).where(ROLE.BUILT_IN.isNull()).execute();
        dsl.deleteFrom(GAME).execute();
    }
}
