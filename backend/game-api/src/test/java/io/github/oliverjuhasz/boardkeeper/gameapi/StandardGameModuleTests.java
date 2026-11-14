package io.github.oliverjuhasz.boardkeeper.gameapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.github.oliverjuhasz.boardkeeper.gameapi.InvalidPlayException.Standard;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StandardGameModuleTests {

    /**
     * A made-up Game for two to four Players. Its pad has birds and eggs, and coins that are
     * entered but not counted. With the "night" Variant on there is a row for owls as well.
     */
    private static ScorePad birdwatch(List<TieBreaker> tieBreakers, boolean recorderDecidesTies) {
        return birdwatch(2, tieBreakers, recorderDecidesTies, null);
    }

    /** The made-up Game, for this many Players or more, and played alone like this, if at all. */
    private static ScorePad birdwatch(
            int minPlayers, List<TieBreaker> tieBreakers, boolean recorderDecidesTies, Solo solo) {
        return new ScorePad(
                minPlayers,
                4,
                List.of(new Variant.OnOff("night", false), new Variant.Choice("board", List.of("front", "back"))),
                List.of(
                        ScoreCategory.of("birds"),
                        ScoreCategory.of("eggs").inStepsOf(2).atMost(10),
                        ScoreCategory.of("owls").onlyWhen("night", "true"),
                        ScoreCategory.of("coins").notCounted()),
                tieBreakers,
                recorderDecidesTies,
                solo);
    }

    private static final Map<String, String> BY_DAY = Map.of();
    private static final Map<String, String> AT_NIGHT = Map.of("night", "true");

    @Nested
    class Totals {

        private final GameModule module = module(birdwatch(List.of(), false));

        @Test
        void addUpTheRowsThatCount() {
            assertThat(results(module, BY_DAY, row(10, 4, 99), row(20, 0, 0)))
                    .containsExactly(new PlayerResult(14, 2, false), new PlayerResult(20, 1, true));
        }

        @Test
        void includeARowThatAVariantPutsOnThePad() {
            assertThat(results(module, AT_NIGHT, night(10, 4, 0, 7), night(20, 0, 0, 0)))
                    .extracting(PlayerResult::total)
                    .containsExactly(21, 20);
        }

        @Test
        void equalTotalsShareAPlacingAndThePlacingAfterSkipsAhead() {
            assertThat(results(module, BY_DAY, row(10, 0, 0), row(20, 0, 0), row(10, 0, 0), row(5, 0, 0)))
                    .extracting(PlayerResult::placing)
                    .containsExactly(2, 1, 2, 4);
        }

        @Test
        void everyoneLevelInFirstPlaceHasWon() {
            assertThat(results(module, BY_DAY, row(20, 0, 0), row(20, 0, 5), row(5, 0, 0)))
                    .containsExactly(new PlayerResult(20, 1, true), new PlayerResult(20, 1, true), new PlayerResult(5, 3, false));
        }
    }

    @Nested
    class TieBreakers {

        @Test
        void putEqualTotalsInOrderFirstOneFirst() {
            GameModule module = module(birdwatch(List.of(TieBreaker.most("eggs"), TieBreaker.most("coins")), false));

            // All three have 20. Eggs: 4, 4, 2. Coins decide between the first two.
            assertThat(results(module, BY_DAY, row(16, 4, 1), row(16, 4, 3), row(18, 2, 9)))
                    .containsExactly(new PlayerResult(20, 2, false), new PlayerResult(20, 1, true), new PlayerResult(20, 3, false));
        }

        @Test
        void canFavourTheFewest() {
            GameModule module = module(birdwatch(List.of(TieBreaker.fewest("coins")), false));

            assertThat(results(module, BY_DAY, row(20, 0, 3), row(20, 0, 1)))
                    .extracting(PlayerResult::placing)
                    .containsExactly(2, 1);
        }

        @Test
        void applyFurtherDownAsWell() {
            GameModule module = module(birdwatch(List.of(TieBreaker.most("coins")), false));

            assertThat(results(module, BY_DAY, row(30, 0, 0), row(20, 0, 1), row(20, 0, 2)))
                    .extracting(PlayerResult::placing)
                    .containsExactly(1, 3, 2);
        }

        @Test
        void leaveASharedPlacingWhenTheyDecideNothing() {
            GameModule module = module(birdwatch(List.of(TieBreaker.most("coins")), false));

            assertThat(score(module, BY_DAY, row(20, 0, 2), row(20, 0, 2)))
                    .isEqualTo(new Outcome.Competitive(
                            List.of(new PlayerResult(20, 1, true), new PlayerResult(20, 1, true)), List.of()));
        }

        @Test
        void thatDependOnARowNotOnThePadAreSkipped() {
            GameModule module = module(birdwatch(List.of(TieBreaker.most("owls"), TieBreaker.most("coins")), false));

            assertThat(results(module, BY_DAY, row(20, 0, 1), row(20, 0, 2)))
                    .extracting(PlayerResult::placing)
                    .containsExactly(2, 1);
        }
    }

    @Nested
    class ATieTheRecorderDecides {

        private final GameModule module = module(birdwatch(List.of(TieBreaker.most("coins")), true));

        @Test
        void isOnlyATieForFirstPlaceThatTheTieBreakersLeave() {
            // Coins decide: nothing to ask.
            assertThat(score(module, BY_DAY, row(20, 0, 2), row(20, 0, 1)).tieForRecorder()).isEmpty();
            // Level in second place: nothing to ask either.
            assertThat(score(module, BY_DAY, row(30, 0, 0), row(20, 0, 1), row(20, 0, 1)).tieForRecorder())
                    .isEmpty();
            // Level in first place on everything entered: the recorder is asked about those two.
            assertThat(score(module, BY_DAY, row(20, 0, 1), row(5, 0, 0), row(20, 0, 1)).tieForRecorder())
                    .containsExactly(0, 2);
        }

        @Test
        void staysSharedUntilTheRecorderMarksAWinner() {
            assertThat(results(module, BY_DAY, row(20, 0, 1), row(20, 0, 1)))
                    .extracting(PlayerResult::placing)
                    .containsExactly(1, 1);
        }

        @Test
        void goesToWhoeverTheRecorderMarks() {
            Outcome.Competitive outcome = score(module, BY_DAY, row(20, 0, 1), marked(row(20, 0, 1)), row(5, 0, 0));

            assertThat(outcome.results())
                    .containsExactly(new PlayerResult(20, 2, false), new PlayerResult(20, 1, true), new PlayerResult(5, 3, false));
            // The question stays open to the same Players, so the answer can be changed.
            assertThat(outcome.tieForRecorder()).containsExactly(0, 1);
        }

        @Test
        void canGoToSomeOfThoseTied() {
            assertThat(results(module, BY_DAY, marked(row(20, 0, 1)), row(20, 0, 1), marked(row(20, 0, 1))))
                    .extracting(PlayerResult::placing)
                    .containsExactly(1, 3, 1);
        }

        @Test
        void cannotGoToAPlayerWhoIsNotPartOfIt() {
            assertRefused(Standard.WINNER_NOT_TIED, module, entry(BY_DAY, row(20, 0, 1), row(20, 0, 1), marked(row(5, 0, 0))));
            // The Tie-breaker already decided this one.
            assertRefused(Standard.WINNER_NOT_TIED, module, entry(BY_DAY, row(20, 0, 2), marked(row(20, 0, 1))));
        }

        @Test
        void isNotOfferedByAPadThatSharesTheWin() {
            GameModule sharing = module(birdwatch(List.of(), false));

            assertThat(score(sharing, BY_DAY, row(20, 0, 0), row(20, 0, 0)).tieForRecorder()).isEmpty();
            assertRefused(Standard.WINNER_NOT_TIED, sharing, entry(BY_DAY, marked(row(20, 0, 0)), row(20, 0, 0)));
        }
    }

    @Nested
    class WithOnlyTheWinnerRecorded {

        private final GameModule module = module(birdwatch(List.of(), true));

        @Test
        void theMarkedPlayersHaveWonAndNobodyIsPlaced() {
            PlayEntry entry = new PlayEntry(
                    RecordingMode.WINNER_ONLY, BY_DAY, List.of(new PlayerEntry(Map.of(), false), new PlayerEntry(Map.of(), true)), null);

            assertThat(module.score(entry))
                    .isEqualTo(new Outcome.Competitive(
                            List.of(new PlayerResult(null, null, false), new PlayerResult(null, null, true)), List.of()));
        }

        @Test
        void aWinnerIsNeededAndThePadStaysEmpty() {
            PlayerEntry nothing = new PlayerEntry(Map.of(), false);
            assertRefused(Standard.WINNER_MISSING, module, new PlayEntry(RecordingMode.WINNER_ONLY, BY_DAY, List.of(nothing, nothing), null));
            assertRefused(
                    Standard.NOT_RECORDED,
                    module,
                    new PlayEntry(RecordingMode.WINNER_ONLY, BY_DAY, List.of(marked(row(1, 0, 0)), nothing), null));
        }
    }

    @Nested
    class Refuses {

        private final GameModule module = module(birdwatch(List.of(), false));

        @Test
        void tooFewOrTooManyPlayers() {
            assertRefused(Standard.TOO_FEW_PLAYERS, module, entry(BY_DAY, row(1, 0, 0)));
            assertRefused(
                    Standard.TOO_MANY_PLAYERS,
                    module,
                    entry(BY_DAY, row(1, 0, 0), row(1, 0, 0), row(1, 0, 0), row(1, 0, 0), row(1, 0, 0)));
        }

        @Test
        void aRowLeftEmpty() {
            PlayerEntry withoutCoins = new PlayerEntry(Map.of("birds", 1, "eggs", 0), false);
            assertRefused(Standard.SCORE_MISSING, module, entry(BY_DAY, withoutCoins, row(1, 0, 0)));
            // At night the owls have to be entered too.
            assertRefused(Standard.SCORE_MISSING, module, entry(AT_NIGHT, row(1, 0, 0), row(1, 0, 0)));
        }

        @Test
        void aValueTheRowDoesNotAllow() {
            assertRefused(Standard.SCORE_NOT_ALLOWED, module, entry(BY_DAY, row(-1, 0, 0), row(1, 0, 0)));
            assertRefused(Standard.SCORE_NOT_ALLOWED, module, entry(BY_DAY, row(1, 3, 0), row(1, 0, 0)));
            assertRefused(Standard.SCORE_NOT_ALLOWED, module, entry(BY_DAY, row(1, 12, 0), row(1, 0, 0)));
        }

        @Test
        void aRowThatIsNotOnThePad() {
            // Owls by day.
            assertRefused(Standard.NOT_RECORDED, module, entry(BY_DAY, night(1, 0, 0, 3), row(1, 0, 0)));
        }

        @Test
        void aSetupThatIsNotOnThePad() {
            assertRefused(Standard.NOT_RECORDED, module, entry(Map.of("weather", "rain"), row(1, 0, 0), row(1, 0, 0)));
            assertRefused(Standard.NOT_RECORDED, module, entry(Map.of("board", "side"), row(1, 0, 0), row(1, 0, 0)));
            assertRefused(Standard.NOT_RECORDED, module, entry(Map.of("night", "yes"), row(1, 0, 0), row(1, 0, 0)));
        }

        @Test
        void aResultForTheGroup() {
            PlayEntry entry = new PlayEntry(
                    RecordingMode.POINTS, BY_DAY, List.of(row(1, 0, 0), row(1, 0, 0)), new GroupEntry(true, Map.of()));
            assertRefused(Standard.NOT_RECORDED, module, entry);
        }
    }

    @Nested
    class ASoloPlay {

        /** The made-up Game, played alone for a score, or against a robot whose score is entered. */
        private final GameModule forScore = module(birdwatch(1, List.of(), true, Solo.forScoreOnly()));

        private final GameModule againstRobot = module(new ScorePad(
                        1,
                        4,
                        List.of(new Variant.Choice("robotLevel", List.of("easy", "hard")).inSoloPlaysOnly()),
                        List.of(ScoreCategory.of("birds"), ScoreCategory.of("robot").notCounted().inSoloPlaysOnly()),
                        List.of(),
                        true,
                        Solo.againstOpponent("robot", true)));

        @Test
        void forScoreOnlyHasATotalButNoWinnerAndNoPlacing() {
            assertThat(score(forScore, BY_DAY, row(30, 4, 2)))
                    .isEqualTo(new Outcome.Competitive(List.of(new PlayerResult(34, null, null)), List.of()));
        }

        @Test
        void forScoreOnlyNeedsTheScores() {
            PlayEntry onlyTheWinner =
                    new PlayEntry(RecordingMode.WINNER_ONLY, BY_DAY, List.of(new PlayerEntry(Map.of(), true)), null);
            assertRefused(Standard.SCORES_NEEDED, forScore, onlyTheWinner);
            assertRefused(Standard.NOT_RECORDED, forScore, entry(BY_DAY, marked(row(30, 4, 2))));
        }

        @Test
        void isNotPossibleForAGameThatIsNotPlayedAlone() {
            // A pad for one Player or more without a way to record Solo Plays: one is still too few.
            assertRefused(Standard.TOO_FEW_PLAYERS, module(birdwatch(1, List.of(), false, null)), entry(BY_DAY, row(30, 4, 2)));
        }

        @Test
        void againstAnOpponentIsWonByBeatingItsScore() {
            assertThat(score(againstRobot, Map.of(), robotPad(50, 42)).results())
                    .containsExactly(new PlayerResult(50, null, true));
            assertThat(score(againstRobot, Map.of(), robotPad(40, 42)).results())
                    .containsExactly(new PlayerResult(40, null, false));
        }

        @Test
        void againstAnOpponentCanBeWonOnATie() {
            assertThat(score(againstRobot, Map.of(), robotPad(42, 42)).results())
                    .containsExactly(new PlayerResult(42, null, true));

            GameModule tieLoses = module(new ScorePad(
                            1,
                            4,
                            List.of(),
                            List.of(ScoreCategory.of("birds"), ScoreCategory.of("robot").notCounted().inSoloPlaysOnly()),
                            List.of(),
                            true,
                            Solo.againstOpponent("robot", false)));
            assertThat(score(tieLoses, Map.of(), robotPad(42, 42)).results())
                    .containsExactly(new PlayerResult(42, null, false));
        }

        @Test
        void againstAnOpponentCanBeRecordedWithOnlyWhetherItWasWon() {
            PlayEntry lost =
                    new PlayEntry(RecordingMode.WINNER_ONLY, Map.of(), List.of(new PlayerEntry(Map.of(), false)), null);
            assertThat(againstRobot.score(lost))
                    .isEqualTo(new Outcome.Competitive(List.of(new PlayerResult(null, null, false)), List.of()));
        }

        @Test
        void hasPartsThatOtherPlaysDoNotHave() {
            Map<String, String> hard = Map.of("robotLevel", "hard");
            assertThat(score(againstRobot, hard, robotPad(50, 42)).results()).hasSize(1);
            // The opponent's score is needed alone, and not with two Players...
            assertRefused(Standard.SCORE_MISSING, againstRobot, entry(hard, new PlayerEntry(Map.of("birds", 50), false)));
            assertRefused(Standard.NOT_RECORDED, againstRobot, entry(Map.of(), robotPad(50, 42), robotPad(40, 42)));
            // ...and neither is the opponent's level.
            PlayerEntry birds = new PlayerEntry(Map.of("birds", 50), false);
            assertRefused(Standard.NOT_RECORDED, againstRobot, entry(hard, birds, birds));
            assertThat(score(againstRobot, Map.of(), birds, birds).results()).hasSize(2);
        }

        @Test
        void describesItselfConsistently() {
            List<Variant> noVariants = List.of();
            List<ScoreCategory> birds = List.of(ScoreCategory.of("birds"));
            // Solo Plays need a pad for one Player or more.
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ScorePad(2, 4, noVariants, birds, List.of(), false, Solo.forScoreOnly()));
            // The opponent's score is a row of its own, for Solo Plays only, which doesn't count.
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ScorePad(1, 4, noVariants, birds, List.of(), false, Solo.againstOpponent("robot", true)))
                    .withMessageContaining("'robot'");
            List<ScoreCategory> countedRobot = List.of(ScoreCategory.of("birds"), ScoreCategory.of("robot").inSoloPlaysOnly());
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ScorePad(
                            1, 4, noVariants, countedRobot, List.of(), false, Solo.againstOpponent("robot", true)));
            // Parts for Solo Plays only need a way to record them.
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ScorePad(
                            1, 4, List.of(new Variant.OnOff("hard", false).inSoloPlaysOnly()), birds, List.of(), false));
        }

        /** The Player's birds, and the robot's score. */
        private static PlayerEntry robotPad(int birds, int robot) {
            return new PlayerEntry(Map.of("birds", birds, "robot", robot), false);
        }
    }

    /**
     * The made-up Game with an Expansion, Hawks, that adds a row for hawks. It is usually won on
     * points, but a Player who gathers a full flock wins outright, and with Hawks, so does one
     * whose hawks strike.
     */
    private static ScorePad birdwatchWithHawks() {
        return new ScorePad(
                2,
                4,
                List.of(new Variant.OnOff("night", false)),
                List.of(ScoreCategory.of("birds"), ScoreCategory.of("hawks").onlyWhen("hawks", Expansion.WITH)),
                List.of(),
                false,
                null,
                List.of(new Expansion("hawks")),
                List.of(
                        VictoryType.onPoints("points"),
                        VictoryType.outright("flock"),
                        VictoryType.outright("strike").onlyWhen("hawks", Expansion.WITH)));
    }

    private static final Map<String, String> WITH_HAWKS = Map.of("hawks", "true");

    @Nested
    class Expansions {

        private final GameModule module = module(birdwatchWithHawks());

        @Test
        void areInTheSetupAndCanAddRows() {
            assertThat(module.score(victory("points", WITH_HAWKS, hawks(10, 5), hawks(12, 0))))
                    .isEqualTo(new Outcome.Competitive(
                            List.of(new PlayerResult(15, 1, true), new PlayerResult(12, 2, false)), List.of()));
        }

        @Test
        void areNotPlayedWithUnlessChosen() {
            ScorePad pad = birdwatchWithHawks();

            assertThat(pad.valueOf("hawks", Map.of())).isEqualTo("false");
            assertThat(pad.categoriesFor(Map.of(), false)).extracting(ScoreCategory::key).containsExactly("birds");
            assertThat(pad.categoriesFor(WITH_HAWKS, false))
                    .extracting(ScoreCategory::key)
                    .containsExactly("birds", "hawks");
            assertRefused(Standard.NOT_RECORDED, module, victory("points", Map.of(), hawks(10, 5), hawks(12, 0)));
        }

        @Test
        void areTrueOrFalse() {
            assertRefused(
                    Standard.NOT_RECORDED,
                    module,
                    victory("points", Map.of("hawks", "maybe"), birds(10), birds(12)));
            assertRefused(
                    Standard.NOT_RECORDED,
                    module,
                    victory("points", Map.of("eagles", "true"), birds(10), birds(12)));
        }
    }

    @Nested
    class VictoryTypes {

        private final GameModule module = module(birdwatchWithHawks());

        @Test
        void onPointsScoreThePad() {
            assertThat(results(victory("points", Map.of(), birds(10), birds(12))))
                    .containsExactly(new PlayerResult(10, 2, false), new PlayerResult(12, 1, true));
        }

        @Test
        void outrightHaveOneMarkedWinnerAndNoScores() {
            assertThat(results(outright("flock", Map.of(), winner(), loser())))
                    .containsExactly(new PlayerResult(null, null, true), new PlayerResult(null, null, false));
            assertRefused(Standard.ONE_WINNER_ONLY, module, outright("flock", Map.of(), winner(), winner()));
            assertRefused(Standard.WINNER_MISSING, module, outright("flock", Map.of(), loser(), loser()));
            // Nobody counted points.
            assertRefused(Standard.NOT_RECORDED, module, victory("flock", Map.of(), birds(10), birds(12)));
            assertRefused(
                    Standard.NOT_RECORDED,
                    module,
                    outright("flock", Map.of(), marked(birds(10)), birds(12)));
        }

        @Test
        void areNeededAndMustBeOnThePad() {
            assertRefused(Standard.VICTORY_TYPE_MISSING, module, victory(null, Map.of(), birds(10), birds(12)));
            assertRefused(Standard.NOT_RECORDED, module, victory("luck", Map.of(), birds(10), birds(12)));
        }

        @Test
        void canDependOnAnExpansion() {
            ScorePad pad = birdwatchWithHawks();

            assertThat(pad.victoryTypesFor(Map.of())).extracting(VictoryType::key).containsExactly("points", "flock");
            assertThat(pad.victoryTypesFor(WITH_HAWKS))
                    .extracting(VictoryType::key)
                    .containsExactly("points", "flock", "strike");
            assertRefused(Standard.NOT_RECORDED, module, outright("strike", Map.of(), winner(), loser()));
            assertThat(results(outright("strike", WITH_HAWKS, winner(), loser()))).hasSize(2);
        }

        @Test
        void areNotRecordedForAGameWonInOneWayOnly() {
            GameModule oneWay = module(birdwatch(List.of(), false));

            assertRefused(
                    Standard.NOT_RECORDED,
                    oneWay,
                    new PlayEntry(RecordingMode.POINTS, Map.of(), List.of(row(1, 0, 0), row(2, 0, 0)), null, "points"));
        }

        @Test
        void areNotRecordedForASoloPlay() {
            GameModule alone = module(new ScorePad(
                    1,
                    4,
                    List.of(),
                    List.of(ScoreCategory.of("birds")),
                    List.of(),
                    false,
                    Solo.forScoreOnly(),
                    List.of(),
                    List.of(VictoryType.onPoints("points"), VictoryType.outright("flock"))));

            assertThat(alone.score(victory(null, Map.of(), birds(10)))).isInstanceOf(Outcome.Competitive.class);
            assertRefused(Standard.NOT_RECORDED, alone, victory("points", Map.of(), birds(10)));
        }

        @Test
        void haveToFitThePad() {
            List<Variant> noVariants = List.of();
            List<ScoreCategory> birds = List.of(ScoreCategory.of("birds"));
            List<Expansion> hawks = List.of(new Expansion("hawks"));

            // One way of winning is no choice.
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ScorePad(
                            2, 4, noVariants, birds, List.of(), false, null, hawks, List.of(VictoryType.onPoints("points"))));
            // The usual way has to be possible with any Setup.
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ScorePad(2, 4, noVariants, birds, List.of(), false, null, hawks, List.of(
                            VictoryType.outright("strike").onlyWhen("hawks", "true"), VictoryType.onPoints("points"))));
            // A condition names a Variant or an Expansion of the pad, with a value it can have.
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ScorePad(2, 4, noVariants, birds, List.of(), false, null, hawks, List.of(
                            VictoryType.onPoints("points"), VictoryType.outright("strike").onlyWhen("eagles", "true"))))
                    .withMessageContaining("'strike'");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ScorePad(2, 4, noVariants, List.of(
                            ScoreCategory.of("hawks").onlyWhen("hawks", "maybe")), List.of(), false, null, hawks, List.of()))
                    .withMessageContaining("'hawks'");
            // Variants and Expansions share the Setup.
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ScorePad(
                            2, 4, List.of(new Variant.OnOff("hawks", false)), birds, List.of(), false, null, hawks, List.of()))
                    .withMessageContaining("'hawks'");
        }

        private List<PlayerResult> results(PlayEntry entry) {
            return ((Outcome.Competitive) module.score(entry)).results();
        }
    }

    /** An entry with scores, won this way. */
    private static PlayEntry victory(String victoryType, Map<String, String> setup, PlayerEntry... players) {
        return new PlayEntry(RecordingMode.POINTS, setup, Arrays.asList(players), null, victoryType);
    }

    /** An entry with only the winner, won this way. */
    private static PlayEntry outright(String victoryType, Map<String, String> setup, PlayerEntry... players) {
        return new PlayEntry(RecordingMode.WINNER_ONLY, setup, Arrays.asList(players), null, victoryType);
    }

    private static PlayerEntry birds(int birds) {
        return new PlayerEntry(Map.of("birds", birds), false);
    }

    private static PlayerEntry hawks(int birds, int hawks) {
        return new PlayerEntry(Map.of("birds", birds, "hawks", hawks), false);
    }

    private static PlayerEntry winner() {
        return new PlayerEntry(Map.of(), true);
    }

    private static PlayerEntry loser() {
        return new PlayerEntry(Map.of(), false);
    }

    @Nested
    class AScorePad {

        @Test
        void givesAVariantItsDefaultWhenNothingWasChosen() {
            ScorePad pad = birdwatch(List.of(), false);

            assertThat(pad.valueOf("night", Map.of())).isEqualTo("false");
            assertThat(pad.valueOf("board", Map.of())).isEqualTo("front");
            assertThat(pad.valueOf("board", Map.of("board", "back"))).isEqualTo("back");
            assertThat(pad.categoriesFor(Map.of(), false))
                    .extracting(ScoreCategory::key)
                    .containsExactly("birds", "eggs", "coins");
            assertThat(pad.categoriesFor(AT_NIGHT, false))
                    .extracting(ScoreCategory::key)
                    .containsExactly("birds", "eggs", "owls", "coins");
        }

        @Test
        void hasToFitTogether() {
            List<Variant> noVariants = List.of();
            List<ScoreCategory> birds = List.of(ScoreCategory.of("birds"));

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ScorePad(2, 4, noVariants, birds, List.of(TieBreaker.most("eggs")), false))
                    .withMessageContaining("'eggs'");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ScorePad(
                            2, 4, noVariants, List.of(ScoreCategory.of("owls").onlyWhen("night", "true")), List.of(), false))
                    .withMessageContaining("'owls'");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ScorePad(
                            2, 4, noVariants, List.of(ScoreCategory.of("birds"), ScoreCategory.of("birds")), List.of(), false))
                    .withMessageContaining("'birds'");
            assertThatIllegalArgumentException().isThrownBy(() -> new ScorePad(3, 2, noVariants, birds, List.of(), false));
        }
    }

    private static GameModule module(ScorePad pad) {
        return new StandardGameModule() {
            @Override
            public UUID id() {
                return UUID.fromString("00000000-0000-7000-8000-000000000001");
            }

            @Override
            public String key() {
                return "birdwatch";
            }

            @Override
            public ScorePad scorePad() {
                return pad;
            }
        };
    }

    private static Outcome.Competitive score(GameModule module, Map<String, String> setup, PlayerEntry... players) {
        return (Outcome.Competitive) module.score(entry(setup, players));
    }

    private static List<PlayerResult> results(GameModule module, Map<String, String> setup, PlayerEntry... players) {
        return score(module, setup, players).results();
    }

    private static PlayEntry entry(Map<String, String> setup, PlayerEntry... players) {
        return new PlayEntry(RecordingMode.POINTS, setup, Arrays.asList(players), null);
    }

    /** A Player's rows by day. */
    private static PlayerEntry row(int birds, int eggs, int coins) {
        return new PlayerEntry(Map.of("birds", birds, "eggs", eggs, "coins", coins), false);
    }

    /** A Player's rows with the "night" Variant on. */
    private static PlayerEntry night(int birds, int eggs, int coins, int owls) {
        return new PlayerEntry(Map.of("birds", birds, "eggs", eggs, "coins", coins, "owls", owls), false);
    }

    private static PlayerEntry marked(PlayerEntry player) {
        return new PlayerEntry(player.scores(), true);
    }

    private static void assertRefused(Standard reason, GameModule module, PlayEntry entry) {
        InvalidPlayException expected = new InvalidPlayException(reason);
        assertThatExceptionOfType(InvalidPlayException.class)
                .isThrownBy(() -> module.score(entry))
                .matches(InvalidPlayException::isStandard, "gives a standard reason")
                .extracting(InvalidPlayException::reason)
                .isEqualTo(expected.reason());
    }
}
