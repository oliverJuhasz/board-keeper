package io.github.oliverjuhasz.boardkeeper.players.internal;

import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.PLAYER;
import static org.jooq.impl.DSL.lower;

import io.github.oliverjuhasz.boardkeeper.players.Player;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class PlayerRepository {

    private final DSLContext dsl;

    PlayerRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public boolean existsByName(String name) {
        return dsl.fetchExists(PLAYER, lower(PLAYER.NAME).eq(name.toLowerCase()));
    }

    public Player insert(String name) {
        UUID id = dsl.insertInto(PLAYER, PLAYER.NAME)
                .values(name)
                .returningResult(PLAYER.ID)
                .fetchSingle()
                .value1();
        return new Player(id, name);
    }

    public Optional<Player> find(UUID id) {
        return dsl.select(PLAYER.ID, PLAYER.NAME)
                .from(PLAYER)
                .where(PLAYER.ID.eq(id))
                .fetchOptional(row -> new Player(row.value1(), row.value2()));
    }
}
