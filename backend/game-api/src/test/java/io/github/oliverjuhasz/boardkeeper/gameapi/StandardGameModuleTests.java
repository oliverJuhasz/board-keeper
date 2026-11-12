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
    private static ScoreSheet sheet(List<TieBreaker> tieBreakers, boolean recorderDecidesTies) {
        return new ScoreSheet(
                2,
                4,
                List.of(new Variant.OnOff("night", false), new Variant.Choice("board", List.of("front", "back"))),
                List.of(
                        ScoreCategory.of("birds"),
                        ScoreCategory.of("eggs").inStepsOf(2).atMost(10),
                        ScoreCategory.of("owls").onlyWhen("night", "true"),
                        ScoreCategory.of("coins").notCounted()),
                tieBreakers,
                recorderDecidesTies);
    }

    private static final Map<String, String> BY_DAY = Map.of();
    private static final Map<String, String> AT_NIGHT = Map.of("night", "true");

    @Nested
    class Totals {

        private final GameModule module = module(sheet(List.of(), false));

        @Test
        void addUpTheRowsThatCount() {
            assertThat(results(module, BY_DAY, pad(10, 4, 99), pad(20, 0, 0)))
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
            assertThat(results(module, BY_DAY, pad(10, 0, 0), pad(20, 0, 0), pad(10, 0, 0), pad(5, 0, 0)))
                    .extracting(PlayerResult::placing)
                    .containsExactly(2, 1, 2, 4);
        }

        @Test
        void everyoneLevelInFirstPlaceHasWon() {
            assertThat(results(module, BY_DAY, pad(20, 0, 0), pad(20, 0, 5), pad(5, 0, 0)))
                    .containsExactly(new PlayerResult(20, 1, true), new PlayerResult(20, 1, true), new PlayerResult(5, 3, false));
        }
    }

    @Nested
    class TieBreakers {

        @Test
        void putEqualTotalsInOrderFirstOneFirst() {
            GameModule module = module(sheet(List.of(TieBreaker.most("eggs"), TieBreaker.most("coins")), false));

            // All three have 20. Eggs: 4, 4, 2. Coins decide between the first two.
            assertThat(results(module, BY_DAY, pad(16, 4, 1), pad(16, 4, 3), pad(18, 2, 9)))
                    .containsExactly(new PlayerResult(20, 2, false), new PlayerResult(20, 1, true), new PlayerResult(20, 3, false));
        }

        @Test
        void canFavourTheFewest() {
            GameModule module = module(sheet(List.of(TieBreaker.fewest("coins")), false));

            assertThat(results(module, BY_DAY, pad(20, 0, 3), pad(20, 0, 1)))
                    .extracting(PlayerResult::placing)
                    .containsExactly(2, 1);
        }

        @Test
        void applyFurtherDownAsWell() {
            GameModule module = module(sheet(List.of(TieBreaker.most("coins")), false));

            assertThat(results(module, BY_DAY, pad(30, 0, 0), pad(20, 0, 1), pad(20, 0, 2)))
                    .extracting(PlayerResult::placing)
                    .containsExactly(1, 3, 2);
        }

        @Test
        void leaveASharedPlacingWhenTheyDecideNothing() {
            GameModule module = module(sheet(List.of(TieBreaker.most("coins")), false));

            assertThat(score(module, BY_DAY, pad(20, 0, 2), pad(20, 0, 2)))
                    .isEqualTo(new Outcome.Competitive(
                            List.of(new PlayerResult(20, 1, true), new PlayerResult(20, 1, true)), List.of()));
        }

        @Test
        void thatDependOnARowNotOnThePadAreSkipped() {
            GameModule module = module(sheet(List.of(TieBreaker.most("owls"), TieBreaker.most("coins")), false));

            assertThat(results(module, BY_DAY, pad(20, 0, 1), pad(20, 0, 2)))
                    .extracting(PlayerResult::placing)
                    .containsExactly(2, 1);
        }
    }

    @Nested
    class ATieTheRecorderDecides {

        private final GameModule module = module(sheet(List.of(TieBreaker.most("coins")), true));

        @Test
        void isOnlyATieForFirstPlaceThatTheTieBreakersLeave() {
            // Coins decide: nothing to ask.
            assertThat(score(module, BY_DAY, pad(20, 0, 2), pad(20, 0, 1)).tieForRecorder()).isEmpty();
            // Level in second place: nothing to ask either.
            assertThat(score(module, BY_DAY, pad(30, 0, 0), pad(20, 0, 1), pad(20, 0, 1)).tieForRecorder())
                    .isEmpty();
            // Level in first place on everything entered: the recorder is asked about those two.
            assertThat(score(module, BY_DAY, pad(20, 0, 1), pad(5, 0, 0), pad(20, 0, 1)).tieForRecorder())
                    .containsExactly(0, 2);
        }

        @Test
        void staysSharedUntilTheRecorderMarksAWinner() {
            assertThat(results(module, BY_DAY, pad(20, 0, 1), pad(20, 0, 1)))
                    .extracting(PlayerResult::placing)
                    .containsExactly(1, 1);
        }

        @Test
        void goesToWhoeverTheRecorderMarks() {
            Outcome.Competitive outcome = score(module, BY_DAY, pad(20, 0, 1), marked(pad(20, 0, 1)), pad(5, 0, 0));

            assertThat(outcome.results())
                    .containsExactly(new PlayerResult(20, 2, false), new PlayerResult(20, 1, true), new PlayerResult(5, 3, false));
            // The question stays open to the same Players, so the answer can be changed.
            assertThat(outcome.tieForRecorder()).containsExactly(0, 1);
        }

        @Test
        void canGoToSomeOfThoseTied() {
            assertThat(results(module, BY_DAY, marked(pad(20, 0, 1)), pad(20, 0, 1), marked(pad(20, 0, 1))))
                    .extracting(PlayerResult::placing)
                    .containsExactly(1, 3, 1);
        }

        @Test
        void cannotGoToAPlayerWhoIsNotPartOfIt() {
            assertRefused(Standard.WINNER_NOT_TIED, module, entry(BY_DAY, pad(20, 0, 1), pad(20, 0, 1), marked(pad(5, 0, 0))));
            // The Tie-breaker already decided this one.
            assertRefused(Standard.WINNER_NOT_TIED, module, entry(BY_DAY, pad(20, 0, 2), marked(pad(20, 0, 1))));
        }

        @Test
        void isNotOfferedByASheetThatSharesTheWin() {
            GameModule sharing = module(sheet(List.of(), false));

            assertThat(score(sharing, BY_DAY, pad(20, 0, 0), pad(20, 0, 0)).tieForRecorder()).isEmpty();
            assertRefused(Standard.WINNER_NOT_TIED, sharing, entry(BY_DAY, marked(pad(20, 0, 0)), pad(20, 0, 0)));
        }
    }

    @Nested
    class WithOnlyTheWinnerRecorded {

        private final GameModule module = module(sheet(List.of(), true));

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
                    new PlayEntry(RecordingMode.WINNER_ONLY, BY_DAY, List.of(marked(pad(1, 0, 0)), nothing), null));
        }
    }

    @Nested
    class Refuses {

        private final GameModule module = module(sheet(List.of(), false));

        @Test
        void tooFewOrTooManyPlayers() {
            assertRefused(Standard.TOO_FEW_PLAYERS, module, entry(BY_DAY, pad(1, 0, 0)));
            assertRefused(
                    Standard.TOO_MANY_PLAYERS,
                    module,
                    entry(BY_DAY, pad(1, 0, 0), pad(1, 0, 0), pad(1, 0, 0), pad(1, 0, 0), pad(1, 0, 0)));
        }

        @Test
        void aRowLeftEmpty() {
            PlayerEntry withoutCoins = new PlayerEntry(Map.of("birds", 1, "eggs", 0), false);
            assertRefused(Standard.SCORE_MISSING, module, entry(BY_DAY, withoutCoins, pad(1, 0, 0)));
            // At night the owls have to be entered too.
            assertRefused(Standard.SCORE_MISSING, module, entry(AT_NIGHT, pad(1, 0, 0), pad(1, 0, 0)));
        }

        @Test
        void aValueTheRowDoesNotAllow() {
            assertRefused(Standard.SCORE_NOT_ALLOWED, module, entry(BY_DAY, pad(-1, 0, 0), pad(1, 0, 0)));
            assertRefused(Standard.SCORE_NOT_ALLOWED, module, entry(BY_DAY, pad(1, 3, 0), pad(1, 0, 0)));
            assertRefused(Standard.SCORE_NOT_ALLOWED, module, entry(BY_DAY, pad(1, 12, 0), pad(1, 0, 0)));
        }

        @Test
        void aRowThatIsNotOnThePad() {
            // Owls by day.
            assertRefused(Standard.NOT_RECORDED, module, entry(BY_DAY, night(1, 0, 0, 3), pad(1, 0, 0)));
        }

        @Test
        void aSetupThatIsNotOnTheSheet() {
            assertRefused(Standard.NOT_RECORDED, module, entry(Map.of("weather", "rain"), pad(1, 0, 0), pad(1, 0, 0)));
            assertRefused(Standard.NOT_RECORDED, module, entry(Map.of("board", "side"), pad(1, 0, 0), pad(1, 0, 0)));
            assertRefused(Standard.NOT_RECORDED, module, entry(Map.of("night", "yes"), pad(1, 0, 0), pad(1, 0, 0)));
        }

        @Test
        void aResultForTheGroup() {
            PlayEntry entry = new PlayEntry(
                    RecordingMode.POINTS, BY_DAY, List.of(pad(1, 0, 0), pad(1, 0, 0)), new GroupEntry(true, Map.of()));
            assertRefused(Standard.NOT_RECORDED, module, entry);
        }
    }

    @Nested
    class AScoreSheet {

        @Test
        void givesAVariantItsDefaultWhenNothingWasChosen() {
            ScoreSheet sheet = sheet(List.of(), false);

            assertThat(sheet.valueOf("night", Map.of())).isEqualTo("false");
            assertThat(sheet.valueOf("board", Map.of())).isEqualTo("front");
            assertThat(sheet.valueOf("board", Map.of("board", "back"))).isEqualTo("back");
            assertThat(sheet.categoriesFor(Map.of())).extracting(ScoreCategory::key).containsExactly("birds", "eggs", "coins");
            assertThat(sheet.categoriesFor(AT_NIGHT))
                    .extracting(ScoreCategory::key)
                    .containsExactly("birds", "eggs", "owls", "coins");
        }

        @Test
        void hasToFitTogether() {
            List<Variant> noVariants = List.of();
            List<ScoreCategory> birds = List.of(ScoreCategory.of("birds"));

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ScoreSheet(2, 4, noVariants, birds, List.of(TieBreaker.most("eggs")), false))
                    .withMessageContaining("'eggs'");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ScoreSheet(
                            2, 4, noVariants, List.of(ScoreCategory.of("owls").onlyWhen("night", "true")), List.of(), false))
                    .withMessageContaining("'owls'");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ScoreSheet(
                            2, 4, noVariants, List.of(ScoreCategory.of("birds"), ScoreCategory.of("birds")), List.of(), false))
                    .withMessageContaining("'birds'");
            assertThatIllegalArgumentException().isThrownBy(() -> new ScoreSheet(3, 2, noVariants, birds, List.of(), false));
        }
    }

    private static GameModule module(ScoreSheet sheet) {
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
            public ScoreSheet scoreSheet() {
                return sheet;
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

    /** A Player's pad by day. */
    private static PlayerEntry pad(int birds, int eggs, int coins) {
        return new PlayerEntry(Map.of("birds", birds, "eggs", eggs, "coins", coins), false);
    }

    /** A Player's pad with the "night" Variant on. */
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
