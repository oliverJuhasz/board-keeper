package io.github.oliverjuhasz.boardkeeper.games.internal;

import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.GAME;
import static org.jooq.impl.DSL.lower;

import io.github.oliverjuhasz.boardkeeper.gameapi.RecordingMode;
import io.github.oliverjuhasz.boardkeeper.games.Game;
import io.github.oliverjuhasz.boardkeeper.games.Game.RecordingDefaults;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record6;
import org.jooq.SelectJoinStep;
import org.springframework.stereotype.Repository;

@Repository
public class GameRepository {

    private final DSLContext dsl;

    GameRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<Game> findAllOrderedByName() {
        return selectGames().orderBy(lower(GAME.NAME), GAME.ID).fetch(GameRepository::toGame);
    }

    public Optional<Game> find(UUID id) {
        return selectGames().where(GAME.ID.eq(id)).fetchOptional(GameRepository::toGame);
    }

    public List<Game> findAll(Collection<UUID> ids) {
        return selectGames().where(GAME.ID.in(ids)).fetch(GameRepository::toGame);
    }

    /** The Game with this name, whatever its capitals. */
    public Optional<UUID> findIdByName(String name) {
        return dsl.select(GAME.ID)
                .from(GAME)
                .where(lower(GAME.NAME).eq(name.toLowerCase()))
                .fetchOptional(row -> row.value1());
    }

    public Game insert(String name, UUID gameModuleId, RecordingDefaults defaults) {
        UUID id = dsl.insertInto(GAME)
                .set(GAME.NAME, name)
                .set(GAME.GAME_MODULE_ID, gameModuleId)
                .set(GAME.COOPERATIVE, defaults.cooperative())
                .set(GAME.RECORDING_MODE, defaults.recordingMode().name())
                .set(GAME.LOWEST_SCORE_WINS, defaults.lowestScoreWins())
                .returningResult(GAME.ID)
                .fetchSingle()
                .value1();
        return new Game(id, name, gameModuleId, defaults);
    }

    public void update(UUID id, String name, RecordingDefaults defaults) {
        dsl.update(GAME)
                .set(GAME.NAME, name)
                .set(GAME.COOPERATIVE, defaults.cooperative())
                .set(GAME.RECORDING_MODE, defaults.recordingMode().name())
                .set(GAME.LOWEST_SCORE_WINS, defaults.lowestScoreWins())
                .where(GAME.ID.eq(id))
                .execute();
    }

    /** False when there is no such Game. */
    public boolean delete(UUID id) {
        return dsl.deleteFrom(GAME).where(GAME.ID.eq(id)).execute() == 1;
    }

    private SelectJoinStep<Record6<UUID, String, UUID, Boolean, String, Boolean>> selectGames() {
        return dsl.select(
                        GAME.ID,
                        GAME.NAME,
                        GAME.GAME_MODULE_ID,
                        GAME.COOPERATIVE,
                        GAME.RECORDING_MODE,
                        GAME.LOWEST_SCORE_WINS)
                .from(GAME);
    }

    private static Game toGame(Record6<UUID, String, UUID, Boolean, String, Boolean> row) {
        return new Game(
                row.value1(),
                row.value2(),
                row.value3(),
                new RecordingDefaults(row.value4(), RecordingMode.valueOf(row.value5()), row.value6()));
    }
}
