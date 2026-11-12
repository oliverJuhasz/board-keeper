package io.github.oliverjuhasz.boardkeeper.plays.internal;

import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.PLAY;
import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.PLAY_RESULT;
import static org.jooq.impl.DSL.multiset;
import static org.jooq.impl.DSL.select;

import io.github.oliverjuhasz.boardkeeper.gameapi.RecordingMode;
import io.github.oliverjuhasz.boardkeeper.games.Game;
import io.github.oliverjuhasz.boardkeeper.plays.NewPlay;
import io.github.oliverjuhasz.boardkeeper.plays.Play;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
public class PlayRepository {

    /**
     * A Play's Results as one column of the Play's row, in the order they were entered.
     * {@code multiset} is jOOQ's way to fetch a row together with its child rows in a single
     * query: the database nests the child rows into the parent's row.
     */
    private static final Field<List<Play.Result>> RESULTS = multiset(select(
                            PLAY_RESULT.PLAYER_ID,
                            PLAY_RESULT.SCORES,
                            PLAY_RESULT.MARKED_WINNER,
                            PLAY_RESULT.TOTAL,
                            PLAY_RESULT.PLACING,
                            PLAY_RESULT.WON)
                    .from(PLAY_RESULT)
                    .where(PLAY_RESULT.PLAY_ID.eq(PLAY.ID))
                    .orderBy(PLAY_RESULT.ENTRY_ORDER))
            .convertFrom(rows -> rows.map(row -> new Play.Result(
                    row.value1(),
                    JsonColumns.toScores(row.value2()),
                    row.value3(),
                    row.value4(),
                    row.value5(),
                    row.value6())));

    private final DSLContext dsl;

    PlayRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Optional<Play> find(UUID id) {
        return dsl.select(PLAY.asterisk(), RESULTS)
                .from(PLAY)
                .where(PLAY.ID.eq(id))
                .fetchOptional(PlayRepository::toPlay);
    }

    /** Stores a Play with its Results and gives the id it got. */
    public UUID insert(Game game, NewPlay newPlay, Play.GroupResult group, List<Play.Result> results, UUID recordedBy) {
        UUID id = dsl.insertInto(PLAY)
                .set(PLAY.GAME_ID, game.id())
                .set(PLAY.GAME_MODULE_ID, game.gameModuleId())
                .set(PLAY.PLAYED_ON, newPlay.playedOn())
                .set(PLAY.LOCATION, newPlay.location())
                .set(PLAY.NOTES, newPlay.notes())
                .set(PLAY.RECORDING_MODE, newPlay.entry().recordingMode().name())
                .set(PLAY.SETUP, JsonColumns.toJson(newPlay.entry().setup()))
                .set(PLAY.COOPERATIVE, group != null)
                .set(PLAY.GROUP_WON, group == null ? null : group.won())
                .set(PLAY.GROUP_SCORES, group == null ? null : JsonColumns.toJson(group.scores()))
                .set(PLAY.GROUP_TOTAL, group == null ? null : group.total())
                .set(PLAY.RECORDED_BY, recordedBy)
                .returningResult(PLAY.ID)
                .fetchSingle()
                .value1();

        // One round trip for all the Results, instead of one each.
        var insertResults = dsl.insertInto(
                PLAY_RESULT,
                PLAY_RESULT.PLAY_ID,
                PLAY_RESULT.PLAYER_ID,
                PLAY_RESULT.ENTRY_ORDER,
                PLAY_RESULT.SCORES,
                PLAY_RESULT.MARKED_WINNER,
                PLAY_RESULT.TOTAL,
                PLAY_RESULT.PLACING,
                PLAY_RESULT.WON);
        for (int i = 0; i < results.size(); i++) {
            Play.Result result = results.get(i);
            insertResults = insertResults.values(
                    id,
                    result.playerId(),
                    i,
                    JsonColumns.toJson(result.scores()),
                    result.markedWinner(),
                    result.total(),
                    result.placing(),
                    result.won());
        }
        insertResults.execute();
        return id;
    }

    private static Play toPlay(Record row) {
        boolean cooperative = row.get(PLAY.COOPERATIVE);
        Play.GroupResult group = cooperative
                ? new Play.GroupResult(
                        row.get(PLAY.GROUP_WON), JsonColumns.toScores(row.get(PLAY.GROUP_SCORES)), row.get(PLAY.GROUP_TOTAL))
                : null;
        return new Play(
                row.get(PLAY.ID),
                row.get(PLAY.GAME_ID),
                row.get(PLAY.GAME_MODULE_ID),
                row.get(PLAY.PLAYED_ON),
                row.get(PLAY.LOCATION),
                row.get(PLAY.NOTES),
                RecordingMode.valueOf(row.get(PLAY.RECORDING_MODE)),
                JsonColumns.toSetup(row.get(PLAY.SETUP)),
                cooperative,
                group,
                row.get(RESULTS),
                row.get(PLAY.RECORDED_BY),
                row.get(PLAY.RECORDED_AT).toInstant());
    }
}
