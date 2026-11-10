package io.github.oliverjuhasz.boardkeeper.players.internal;

import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.PLAYER;
import static org.jooq.impl.DSL.lower;

import io.github.oliverjuhasz.boardkeeper.players.Player;
import java.util.List;
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

    public List<Player> findAllOrderedByName() {
        return dsl.select(PLAYER.ID, PLAYER.NAME)
                .from(PLAYER)
                .orderBy(lower(PLAYER.NAME), PLAYER.ID)
                .fetch(row -> new Player(row.value1(), row.value2()));
    }

    public Optional<Player> find(UUID id) {
        return dsl.select(PLAYER.ID, PLAYER.NAME)
                .from(PLAYER)
                .where(PLAYER.ID.eq(id))
                .fetchOptional(row -> new Player(row.value1(), row.value2()));
    }

    /** The Player with this name, whatever its capitals. */
    public Optional<UUID> findIdByName(String name) {
        return dsl.select(PLAYER.ID)
                .from(PLAYER)
                .where(lower(PLAYER.NAME).eq(name.toLowerCase()))
                .fetchOptional(row -> row.value1());
    }

    public Player insert(String name) {
        UUID id = dsl.insertInto(PLAYER, PLAYER.NAME)
                .values(name)
                .returningResult(PLAYER.ID)
                .fetchSingle()
                .value1();
        return new Player(id, name);
    }

    /** False when there is no such Player. */
    public boolean rename(UUID id, String name) {
        return dsl.update(PLAYER).set(PLAYER.NAME, name).where(PLAYER.ID.eq(id)).execute() == 1;
    }

    /** False when there is no such Player. */
    public boolean delete(UUID id) {
        return dsl.deleteFrom(PLAYER).where(PLAYER.ID.eq(id)).execute() == 1;
    }
}
