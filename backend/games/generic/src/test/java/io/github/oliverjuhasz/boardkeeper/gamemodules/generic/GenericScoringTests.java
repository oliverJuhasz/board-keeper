package io.github.oliverjuhasz.boardkeeper.gamemodules.generic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.github.oliverjuhasz.boardkeeper.gameapi.GroupEntry;
import io.github.oliverjuhasz.boardkeeper.gameapi.InvalidPlayException;
import io.github.oliverjuhasz.boardkeeper.gameapi.Outcome;
import io.github.oliverjuhasz.boardkeeper.gameapi.PlayEntry;
import io.github.oliverjuhasz.boardkeeper.gameapi.PlayerEntry;
import io.github.oliverjuhasz.boardkeeper.gameapi.PlayerResult;
import io.github.oliverjuhasz.boardkeeper.gameapi.RecordingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GenericScoringTests {

    private static final Map<String, String> COMPETITIVE = Map.of();
    private static final Map<String, String> LOWEST_WINS = Map.of("lowestScoreWins", "true");
    private static final Map<String, String> COOPERATIVE = Map.of("cooperative", "true");

    private final GenericGameModule module = new GenericGameModule();

    @Nested
    class ACompetitivePlayInPoints {

        @Test
        void placesThePlayersByTotalHighestFirst() {
            assertThat(points(COMPETITIVE, scored(40), scored(55), scored(-3)))
                    .containsExactly(result(40, 2, false), result(55, 1, true), result(-3, 3, false));
        }

        @Test
        void placesTheLowestTotalFirstWhenTheLowestScoreWins() {
            assertThat(points(LOWEST_WINS, scored(40), scored(55), scored(-3)))
                    .containsExactly(result(40, 2, false), result(55, 3, false), result(-3, 1, true));
        }

        @Test
        void givesEqualTotalsTheSamePlacingAndSkipsThePlacingsTheyUseUp() {
            assertThat(points(COMPETITIVE, scored(30), scored(50), scored(30), scored(10)))
                    .extracting(PlayerResult::placing)
                    .containsExactly(2, 1, 2, 4);
        }

        @Test
        void everyoneTiedForFirstPlaceHasWonUnlessTheRecorderDecides() {
            assertThat(points(COMPETITIVE, scored(50), scored(50), scored(20)))
                    .containsExactly(result(50, 1, true), result(50, 1, true), result(20, 3, false));
        }

        @Test
        void theRecorderCanDecideATieForFirstPlace() {
            assertThat(points(COMPETITIVE, scored(50), tieWinner(50), scored(20)))
                    .containsExactly(result(50, 2, false), result(50, 1, true), result(20, 3, false));
        }

        @Test
        void theRecorderCanGiveFirstPlaceToSomeOfThoseTiedForIt() {
            assertThat(points(COMPETITIVE, tieWinner(50), scored(50), tieWinner(50), scored(20)))
                    .extracting(PlayerResult::placing)
                    .containsExactly(1, 3, 1, 4);
        }

        @Test
        void markingEveryoneTiedForFirstPlaceLeavesItShared() {
            assertThat(points(COMPETITIVE, tieWinner(50), tieWinner(50), scored(20)))
                    .extracting(PlayerResult::placing)
                    .containsExactly(1, 1, 3);
        }

        @Test
        void markingTheOnlyPlayerInFirstPlaceChangesNothing() {
            assertThat(points(COMPETITIVE, tieWinner(50), scored(20)))
                    .containsExactly(result(50, 1, true), result(20, 2, false));
        }

        @Test
        void aTieForFirstPlaceIsTheRecordersToDecide() {
            assertThat(tieForRecorder(entry(RecordingMode.POINTS, COMPETITIVE, scored(50), scored(20), scored(50))))
                    .containsExactly(0, 2);
            // The question stays open after it was answered, so the answer can be changed.
            assertThat(tieForRecorder(entry(RecordingMode.POINTS, COMPETITIVE, tieWinner(50), scored(50))))
                    .containsExactly(0, 1);
            assertThat(tieForRecorder(entry(RecordingMode.POINTS, COMPETITIVE, scored(50), scored(20)))).isEmpty();
            assertThat(tieForRecorder(entry(RecordingMode.POINTS, COMPETITIVE, scored(50), scored(20), scored(20))))
                    .isEmpty();
        }

        @Test
        void aPlayerWithoutTheBestTotalCannotBeMarkedAsTheWinner() {
            assertRefused("winnerNotInFirstPlace", entry(RecordingMode.POINTS, COMPETITIVE, scored(50), tieWinner(20)));
            assertRefused("winnerNotInFirstPlace", entry(RecordingMode.POINTS, LOWEST_WINS, tieWinner(50), scored(20)));
        }

        @Test
        void everyPlayerNeedsAScore() {
            assertRefused("scoreMissing", entry(RecordingMode.POINTS, COMPETITIVE, scored(50), notScored()));
        }

        @Test
        void recordsNothingButTheTotal() {
            PlayerEntry withACategory = new PlayerEntry(Map.of("total", 50, "eggs", 12), false);
            assertRefused("notRecorded", entry(RecordingMode.POINTS, COMPETITIVE, withACategory, scored(20)));
        }
    }

    @Nested
    class ACompetitivePlayWithOnlyTheWinner {

        @Test
        void theMarkedPlayersHaveWonAndNobodyIsPlaced() {
            assertThat(competitive(entry(RecordingMode.WINNER_ONLY, COMPETITIVE, notScored(), winner(), winner())))
                    .containsExactly(result(null, null, false), result(null, null, true), result(null, null, true));
        }

        @Test
        void needsAtLeastOneWinner() {
            assertRefused("winnerMissing", entry(RecordingMode.WINNER_ONLY, COMPETITIVE, notScored(), notScored()));
        }

        @Test
        void recordsNoScores() {
            assertRefused("notRecorded", entry(RecordingMode.WINNER_ONLY, COMPETITIVE, tieWinner(50), scored(20)));
        }
    }

    @Nested
    class ACompetitivePlay {

        @Test
        void needsAtLeastTwoPlayers() {
            assertRefused("tooFewPlayers", entry(RecordingMode.POINTS, COMPETITIVE, scored(50)));
            assertRefused("tooFewPlayers", entry(RecordingMode.WINNER_ONLY, COMPETITIVE, winner()));
        }

        @Test
        void hasNoResultForTheGroup() {
            assertRefused(
                    "notRecorded",
                    new PlayEntry(
                            RecordingMode.POINTS,
                            COMPETITIVE,
                            List.of(scored(50), scored(20)),
                            new GroupEntry(true, Map.of())));
        }
    }

    @Nested
    class ACooperativePlay {

        @Test
        void isWonOrLostByTheGroup() {
            assertThat(module.score(cooperative(RecordingMode.WINNER_ONLY, new GroupEntry(true, Map.of()), 3)))
                    .isEqualTo(new Outcome.Cooperative(true, null));
            assertThat(module.score(cooperative(RecordingMode.WINNER_ONLY, new GroupEntry(false, Map.of()), 3)))
                    .isEqualTo(new Outcome.Cooperative(false, null));
        }

        @Test
        void canHaveAGroupScoreWhenRecordedInPoints() {
            assertThat(module.score(cooperative(RecordingMode.POINTS, new GroupEntry(false, Map.of("total", 17)), 2)))
                    .isEqualTo(new Outcome.Cooperative(false, 17));
            assertThat(module.score(cooperative(RecordingMode.POINTS, new GroupEntry(true, Map.of()), 2)))
                    .isEqualTo(new Outcome.Cooperative(true, null));
        }

        @Test
        void canBeASoloPlay() {
            assertThat(module.score(cooperative(RecordingMode.WINNER_ONLY, new GroupEntry(true, Map.of()), 1)))
                    .isEqualTo(new Outcome.Cooperative(true, null));
        }

        @Test
        void needsToSayWhetherTheGroupWon() {
            assertRefused("groupResultMissing", cooperative(RecordingMode.POINTS, null, 2));
        }

        @Test
        void hasNoGroupScoreWhenOnlyTheOutcomeIsRecorded() {
            assertRefused(
                    "notRecorded", cooperative(RecordingMode.WINNER_ONLY, new GroupEntry(true, Map.of("total", 17)), 2));
        }

        @Test
        void recordsNothingForThePlayersOneByOne() {
            GroupEntry won = new GroupEntry(true, Map.of());
            assertRefused(
                    "notRecorded", new PlayEntry(RecordingMode.POINTS, COOPERATIVE, List.of(scored(5), notScored()), won));
            assertRefused(
                    "notRecorded",
                    new PlayEntry(RecordingMode.WINNER_ONLY, COOPERATIVE, List.of(winner(), notScored()), won));
        }
    }

    @Test
    void aPlayNeedsPlayers() {
        assertRefused("tooFewPlayers", entry(RecordingMode.POINTS, COMPETITIVE));
        assertRefused("tooFewPlayers", cooperative(RecordingMode.POINTS, new GroupEntry(true, Map.of()), 0));
    }

    @Test
    void knowsOnlyItsOwnTwoVariants() {
        assertRefused("notRecorded", entry(RecordingMode.POINTS, Map.of("boardSide", "A"), scored(50), scored(20)));
        assertRefused("notRecorded", entry(RecordingMode.POINTS, Map.of("cooperative", "yes"), scored(50), scored(20)));
    }

    /** Every reason the module can give has a text in English, the language the others fall back to. */
    @Test
    void explainsEveryRefusalInItsTranslationFile() throws Exception {
        Properties english = new Properties();
        try (var file = getClass().getResourceAsStream("/i18n/generic_en.properties")) {
            english.load(file);
        }
        for (String reason : List.of(
                "tooFewPlayers",
                "scoreMissing",
                "winnerMissing",
                "winnerNotInFirstPlace",
                "groupResultMissing",
                "notRecorded")) {
            assertThat(english).containsKey("generic.invalid." + reason);
        }
    }

    private List<PlayerResult> points(Map<String, String> setup, PlayerEntry... players) {
        return competitive(entry(RecordingMode.POINTS, setup, players));
    }

    private List<PlayerResult> competitive(PlayEntry entry) {
        Outcome outcome = module.score(entry);
        assertThat(outcome).isInstanceOf(Outcome.Competitive.class);
        return ((Outcome.Competitive) outcome).results();
    }

    private List<Integer> tieForRecorder(PlayEntry entry) {
        return ((Outcome.Competitive) module.score(entry)).tieForRecorder();
    }

    private void assertRefused(String reason, PlayEntry entry) {
        assertThatExceptionOfType(InvalidPlayException.class)
                .isThrownBy(() -> module.score(entry))
                .extracting(InvalidPlayException::reason)
                .isEqualTo(reason);
    }

    private static PlayEntry entry(RecordingMode recordingMode, Map<String, String> setup, PlayerEntry... players) {
        return new PlayEntry(recordingMode, setup, Arrays.asList(players), null);
    }

    private static PlayEntry cooperative(RecordingMode recordingMode, GroupEntry group, int players) {
        return new PlayEntry(recordingMode, COOPERATIVE, java.util.Collections.nCopies(players, notScored()), group);
    }

    private static PlayerEntry scored(int total) {
        return new PlayerEntry(Map.of("total", total), false);
    }

    /** A Player with a score whom the recorder also marked as the winner, to decide a tie. */
    private static PlayerEntry tieWinner(int total) {
        return new PlayerEntry(Map.of("total", total), true);
    }

    private static PlayerEntry winner() {
        return new PlayerEntry(Map.of(), true);
    }

    private static PlayerEntry notScored() {
        return new PlayerEntry(Map.of(), false);
    }

    private static PlayerResult result(Integer total, Integer placing, boolean won) {
        return new PlayerResult(total, placing, won);
    }
}
