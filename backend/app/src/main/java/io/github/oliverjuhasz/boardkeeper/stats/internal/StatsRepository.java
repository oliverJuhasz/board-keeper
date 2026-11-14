package io.github.oliverjuhasz.boardkeeper.stats.internal;

import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.STATS_PLAY;
import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.STATS_RESULT;
import static org.jooq.impl.DSL.avg;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.min;

import io.github.oliverjuhasz.boardkeeper.plays.Play;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** The module's copy of the Plays, and the sums worked out from it. */
@Repository
class StatsRepository {

    /** A JSON mapper of the module's own, for the same reason as the plays module's. */
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final TypeReference<Map<String, Integer>> SCORES = new TypeReference<>() {};

    private final DSLContext dsl;

    StatsRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Waits until no other transaction is copying the same Play, and keeps others waiting until
     * this one ends. It uses a PostgreSQL advisory lock: a lock on a number of the application's
     * choosing rather than on a row, here made from the Play's id. The lock is let go when the
     * transaction ends. Two Plays that happen to make the same number only wait for each other.
     */
    void lock(UUID playId) {
        dsl.execute("select pg_advisory_xact_lock(?)", playId.getMostSignificantBits() ^ playId.getLeastSignificantBits());
    }

    boolean isEmpty() {
        return !dsl.fetchExists(STATS_PLAY);
    }

    /** Copies a Play, in place of any earlier copy of it. */
    void save(Play play) {
        delete(play.id());
        dsl.insertInto(STATS_PLAY)
                .set(STATS_PLAY.PLAY_ID, play.id())
                .set(STATS_PLAY.GAME_ID, play.gameId())
                .set(STATS_PLAY.GAME_MODULE_ID, play.gameModuleId())
                .set(STATS_PLAY.PLAYED_ON, play.playedOn())
                .set(STATS_PLAY.COOPERATIVE, play.cooperative())
                .set(STATS_PLAY.SOLO, play.solo())
                .set(STATS_PLAY.GROUP_WON, play.group() == null ? null : play.group().won())
                .set(STATS_PLAY.GROUP_TOTAL, play.group() == null ? null : play.group().total())
                .set(STATS_PLAY.VICTORY_TYPE, play.victoryType())
                .execute();
        var results = dsl.insertInto(
                STATS_RESULT,
                STATS_RESULT.PLAY_ID,
                STATS_RESULT.PLAYER_ID,
                STATS_RESULT.TOTAL,
                STATS_RESULT.PLACING,
                STATS_RESULT.WON,
                STATS_RESULT.SCORES);
        for (Play.Result result : play.results()) {
            results = results.values(
                    play.id(),
                    result.playerId(),
                    result.total(),
                    result.placing(),
                    result.won(),
                    JSONB.valueOf(JSON.writeValueAsString(result.scores())));
        }
        results.execute();
    }

    /** Removes the copy of a Play, if there is one. Its Results go with it. */
    void delete(UUID playId) {
        dsl.deleteFrom(STATS_PLAY).where(STATS_PLAY.PLAY_ID.eq(playId)).execute();
    }

    /** How many times a Game was played, and when last. */
    PlayCount playCount(UUID gameId) {
        return dsl.select(count(), max(STATS_PLAY.PLAYED_ON))
                .from(STATS_PLAY)
                .where(STATS_PLAY.GAME_ID.eq(gameId))
                .fetchSingle(row -> new PlayCount(row.value1(), row.value2()));
    }

    /**
     * What each Player got in the Plays of a Game that weren't cooperative: in the Solo Plays
     * when {@code solo}, otherwise in those where the Players played against each other.
     */
    List<PlayerTally> playerTallies(UUID gameId, boolean solo) {
        return dsl.select(
                        STATS_RESULT.PLAYER_ID,
                        count(),
                        // "filter (where ...)": counts only the rows for which the condition holds.
                        count().filterWhere(STATS_RESULT.WON.isTrue()),
                        // Counting a column counts the rows in which it isn't null.
                        count(STATS_RESULT.WON),
                        max(STATS_RESULT.TOTAL),
                        min(STATS_RESULT.TOTAL),
                        // Averages leave out the rows without a value, here those without scores.
                        avg(STATS_RESULT.TOTAL),
                        avg(STATS_RESULT.PLACING))
                .from(STATS_RESULT)
                .join(STATS_PLAY)
                .on(STATS_PLAY.PLAY_ID.eq(STATS_RESULT.PLAY_ID))
                .where(STATS_PLAY.GAME_ID.eq(gameId), STATS_PLAY.COOPERATIVE.isFalse(), STATS_PLAY.SOLO.eq(solo))
                .groupBy(STATS_RESULT.PLAYER_ID)
                .fetch(row -> new PlayerTally(
                        row.value1(),
                        row.value2(),
                        row.value3(),
                        row.value4(),
                        row.value5(),
                        row.value6(),
                        toDouble(row.value7()),
                        toDouble(row.value8())));
    }

    /**
     * How many Plays of a Game were won in each way, counting the Plays the given Game Module
     * scored, whose Victory Types are its own. The most first.
     */
    List<VictoryTypeCount> victoryTypes(UUID gameId, UUID gameModuleId) {
        return dsl.select(STATS_PLAY.VICTORY_TYPE, count())
                .from(STATS_PLAY)
                .where(
                        STATS_PLAY.GAME_ID.eq(gameId),
                        STATS_PLAY.GAME_MODULE_ID.eq(gameModuleId),
                        STATS_PLAY.VICTORY_TYPE.isNotNull())
                .groupBy(STATS_PLAY.VICTORY_TYPE)
                .orderBy(count().desc(), STATS_PLAY.VICTORY_TYPE)
                .fetch(row -> new VictoryTypeCount(row.value1(), row.value2()));
    }

    /** How the group did in the Cooperative Plays of a Game. Null when there are none. */
    GroupTally groupTally(UUID gameId) {
        return dsl.select(
                        count(),
                        count().filterWhere(STATS_PLAY.GROUP_WON.isTrue()),
                        max(STATS_PLAY.GROUP_TOTAL),
                        avg(STATS_PLAY.GROUP_TOTAL))
                .from(STATS_PLAY)
                .where(STATS_PLAY.GAME_ID.eq(gameId), STATS_PLAY.COOPERATIVE.isTrue())
                .fetchSingle(row -> row.value1() == 0
                        ? null
                        : new GroupTally(row.value1(), row.value2(), row.value3(), toDouble(row.value4())));
    }

    /**
     * The scores entered for each Player in the Plays of a Game in which the Players played
     * against each other, if the given Game Module scored them.
     */
    List<Map<String, Integer>> competitiveScores(UUID gameId, UUID gameModuleId) {
        return dsl.select(STATS_RESULT.SCORES)
                .from(STATS_RESULT)
                .join(STATS_PLAY)
                .on(STATS_PLAY.PLAY_ID.eq(STATS_RESULT.PLAY_ID))
                .where(
                        STATS_PLAY.GAME_ID.eq(gameId),
                        STATS_PLAY.GAME_MODULE_ID.eq(gameModuleId),
                        STATS_PLAY.COOPERATIVE.isFalse(),
                        STATS_PLAY.SOLO.isFalse())
                .fetch(row -> JSON.readValue(row.value1().data(), SCORES));
    }

    /** All Plays a Player took part in, whatever the Game. */
    PlayerTotals totalsOf(UUID playerId) {
        Condition againstOthers = STATS_PLAY.SOLO.isFalse().and(STATS_PLAY.COOPERATIVE.isFalse());
        Condition together = STATS_PLAY.COOPERATIVE.isTrue();
        return dsl.select(
                        count(),
                        max(STATS_PLAY.PLAYED_ON),
                        count().filterWhere(againstOthers),
                        count().filterWhere(againstOthers.and(STATS_RESULT.WON.isTrue())),
                        // Only a Play against others has Placings.
                        avg(STATS_RESULT.PLACING),
                        count().filterWhere(together),
                        count().filterWhere(together.and(STATS_RESULT.WON.isTrue())),
                        count().filterWhere(STATS_PLAY.SOLO.isTrue()))
                .from(STATS_RESULT)
                .join(STATS_PLAY)
                .on(STATS_PLAY.PLAY_ID.eq(STATS_RESULT.PLAY_ID))
                .where(STATS_RESULT.PLAYER_ID.eq(playerId))
                .fetchSingle(row -> new PlayerTotals(
                        row.value1(),
                        row.value2(),
                        row.value3(),
                        row.value4(),
                        toDouble(row.value5()),
                        row.value6(),
                        row.value7(),
                        row.value8()));
    }

    /** What a Player got in each Game they played. */
    List<GameTally> gameTalliesOf(UUID playerId) {
        Condition withOthers = STATS_PLAY.SOLO.isFalse();
        Condition againstOthers = withOthers.and(STATS_PLAY.COOPERATIVE.isFalse());
        Condition solo = STATS_PLAY.SOLO.isTrue();
        return dsl.select(
                        STATS_PLAY.GAME_ID,
                        max(STATS_PLAY.PLAYED_ON),
                        count().filterWhere(withOthers),
                        // In a Cooperative Play, every Player has the group's result.
                        count().filterWhere(withOthers.and(STATS_RESULT.WON.isTrue())),
                        max(STATS_RESULT.TOTAL).filterWhere(againstOthers),
                        min(STATS_RESULT.TOTAL).filterWhere(againstOthers),
                        avg(STATS_RESULT.TOTAL).filterWhere(againstOthers),
                        avg(STATS_RESULT.PLACING),
                        count().filterWhere(solo),
                        count().filterWhere(solo.and(STATS_RESULT.WON.isTrue())),
                        count(STATS_RESULT.WON).filterWhere(solo),
                        max(STATS_RESULT.TOTAL).filterWhere(solo),
                        min(STATS_RESULT.TOTAL).filterWhere(solo),
                        avg(STATS_RESULT.TOTAL).filterWhere(solo))
                .from(STATS_RESULT)
                .join(STATS_PLAY)
                .on(STATS_PLAY.PLAY_ID.eq(STATS_RESULT.PLAY_ID))
                .where(STATS_RESULT.PLAYER_ID.eq(playerId))
                .groupBy(STATS_PLAY.GAME_ID)
                .fetch(row -> new GameTally(
                        row.value1(),
                        row.value2(),
                        new PlayerTally(
                                playerId,
                                row.value3(),
                                row.value4(),
                                row.value3(),
                                row.value5(),
                                row.value6(),
                                toDouble(row.value7()),
                                toDouble(row.value8())),
                        new PlayerTally(
                                playerId,
                                row.value9(),
                                row.value10(),
                                row.value11(),
                                row.value12(),
                                row.value13(),
                                toDouble(row.value14()),
                                null)));
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    record PlayCount(int plays, LocalDate lastPlayedOn) {}

    /**
     * @param playsWonOrLost the Plays in which the Player won or lost: all of them, except Solo
     *     Plays recorded for a score only
     * @param highestScore null, like the other scores, when no score was recorded
     */
    record PlayerTally(
            UUID playerId,
            int plays,
            int wins,
            int playsWonOrLost,
            Integer highestScore,
            Integer lowestScore,
            Double averageScore,
            Double averagePlacing) {}

    record GroupTally(int plays, int wins, Integer highestScore, Double averageScore) {}

    record VictoryTypeCount(String victoryType, int plays) {}

    /**
     * @param plays every Play the Player took part in
     * @param averagePlacing in the Plays against others recorded with scores
     */
    record PlayerTotals(
            int plays,
            LocalDate lastPlayedOn,
            int playsAgainstOthers,
            int winsAgainstOthers,
            Double averagePlacing,
            int playsTogether,
            int winsTogether,
            int soloPlays) {}

    /**
     * @param withOthers the Plays with other Players: against them, or together with them; the
     *     scores and Placings come from those against them only
     * @param solo the Solo Plays
     */
    record GameTally(UUID gameId, LocalDate lastPlayedOn, PlayerTally withOthers, PlayerTally solo) {}
}
