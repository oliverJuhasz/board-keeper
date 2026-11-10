package io.github.oliverjuhasz.boardkeeper.games.internal;

import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.GAME;

import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
class GameRepository {

    private final DSLContext dsl;

    GameRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    List<GameRow> findAllOrderedByName() {
        return dsl.select(GAME.ID, GAME.NAME, GAME.GAME_MODULE_ID)
                .from(GAME)
                .orderBy(GAME.NAME, GAME.ID)
                .fetch(row -> new GameRow(row.value1(), row.value2(), row.value3()));
    }

    record GameRow(UUID id, String name, UUID gameModuleId) {}
}
