package io.github.oliverjuhasz.boardkeeper.plays.internal;

import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.PLAY;
import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.PLAY_RESULT;
import static org.jooq.impl.DSL.exists;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.multiset;
import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.selectOne;

import io.github.oliverjuhasz.boardkeeper.gameapi.RecordingMode;
import io.github.oliverjuhasz.boardkeeper.games.Game;
import io.github.oliverjuhasz.boardkeeper.plays.NewPlay;
import io.github.oliverjuhasz.boardkeeper.plays.Play;
import io.github.oliverjuhasz.boardkeeper.plays.PlayHistory;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;
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

    /**
     * The Plays that pass the filter, from the newest day back, and within a day from the last
     * one recorded: ids are handed out in the order of recording.
     *
     * @param after the Play to start after; null to start at the newest
     */
    public List<Play> findPage(PlayHistory.Filter filter, PlayHistory.Position after, int limit) {
        Condition passesFilter = noCondition();
        if (filter.gameId() != null) {
            passesFilter = passesFilter.and(PLAY.GAME_ID.eq(filter.gameId()));
        }
        for (UUID playerId : filter.playerIds()) {
            passesFilter = passesFilter.and(exists(selectOne()
                    .from(PLAY_RESULT)
                    .where(PLAY_RESULT.PLAY_ID.eq(PLAY.ID))
                    .and(PLAY_RESULT.PLAYER_ID.eq(playerId))));
        }
        if (filter.from() != null) {
            passesFilter = passesFilter.and(PLAY.PLAYED_ON.ge(filter.from()));
        }
        if (filter.to() != null) {
            passesFilter = passesFilter.and(PLAY.PLAYED_ON.le(filter.to()));
        }

        var newestFirst = dsl.select(PLAY.asterisk(), RESULTS)
                .from(PLAY)
                .where(passesFilter)
                .orderBy(PLAY.PLAYED_ON.desc(), PLAY.ID.desc());
        // seek() continues after a given row of the ordering, where offset() would count rows
        // to skip. It turns into "where (played_on, id) < (?, ?)", which the index on those two
        // columns answers directly.
        var page = after == null
                ? newestFirst.limit(limit)
                : newestFirst.seek(after.playedOn(), after.id()).limit(limit);
        return page.fetch(PlayRepository::toPlay);
    }

    public List<UUID> allIds() {
        return dsl.select(PLAY.ID).from(PLAY).fetch(row -> row.value1());
    }

    /** The day each Game was last played on, by the Game's id. A Game without Plays is missing. */
    public Map<UUID, LocalDate> lastPlayedOnByGame() {
        return dsl.select(PLAY.GAME_ID, max(PLAY.PLAYED_ON))
                .from(PLAY)
                .groupBy(PLAY.GAME_ID)
                .fetchMap(row -> row.value1(), row -> row.value2());
    }

    /** Stores a Play with its Results and gives the id it got. */
    /**
     * Stores a new Play with its Results.
     *
     * @param id the Play's id, or null to have the database make one
     * @return the Play's id; empty when a Play with the given id exists already, in which case
     *     nothing was stored
     */
    public Optional<UUID> insert(
            UUID id, Game game, NewPlay newPlay, Play.GroupResult group, List<Play.Result> results, UUID recordedBy) {
        Optional<UUID> inserted = dsl.insertInto(PLAY)
                // DSL.defaultValue: the column's default, uuidv7(), when no id was given.
                .set(PLAY.ID, id == null ? DSL.defaultValue(PLAY.ID) : DSL.val(id))
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
                // "on conflict do nothing": a second Play with the same id is not stored, and
                // returns no row, instead of failing the whole transaction.
                .onConflictDoNothing()
                .returningResult(PLAY.ID)
                .fetchOptional(row -> row.value1());
        inserted.ifPresent(recorded -> insertResults(recorded, results));
        return inserted;
    }

    /** Replaces what was entered for a Play and what was calculated from it. Its Game and its recorder stay. */
    public void update(UUID id, NewPlay changed, Play.GroupResult group, List<Play.Result> results) {
        dsl.update(PLAY)
                .set(PLAY.PLAYED_ON, changed.playedOn())
                .set(PLAY.LOCATION, changed.location())
                .set(PLAY.NOTES, changed.notes())
                .set(PLAY.RECORDING_MODE, changed.entry().recordingMode().name())
                .set(PLAY.SETUP, JsonColumns.toJson(changed.entry().setup()))
                .set(PLAY.COOPERATIVE, group != null)
                .set(PLAY.GROUP_WON, group == null ? null : group.won())
                .set(PLAY.GROUP_SCORES, group == null ? null : JsonColumns.toJson(group.scores()))
                .set(PLAY.GROUP_TOTAL, group == null ? null : group.total())
                .where(PLAY.ID.eq(id))
                .execute();
        // The Players themselves may have changed, so the Results are written anew.
        dsl.deleteFrom(PLAY_RESULT).where(PLAY_RESULT.PLAY_ID.eq(id)).execute();
        insertResults(id, results);
    }

    /** Deletes a Play. Its Results go with it, through the foreign key's "on delete cascade". */
    public void delete(UUID id) {
        dsl.deleteFrom(PLAY).where(PLAY.ID.eq(id)).execute();
    }

    /** One round trip for all the Results, instead of one each. */
    private void insertResults(UUID playId, List<Play.Result> results) {
        var insert = dsl.insertInto(
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
            insert = insert.values(
                    playId,
                    result.playerId(),
                    i,
                    JsonColumns.toJson(result.scores()),
                    result.markedWinner(),
                    result.total(),
                    result.placing(),
                    result.won());
        }
        insert.execute();
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
