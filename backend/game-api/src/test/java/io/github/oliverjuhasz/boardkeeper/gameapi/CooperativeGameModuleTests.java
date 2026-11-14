package io.github.oliverjuhasz.boardkeeper.gameapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import io.github.oliverjuhasz.boardkeeper.gameapi.InvalidPlayException.Standard;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CooperativeGameModuleTests {

    /**
     * A made-up Cooperative Game for one to four Players, who defend a castle against a foe. The
     * group counts the walls left standing and the towers lost; the score is ten points a wall
     * and five a level of the foe, for a win, less a point per tower. Each Player picks a knight,
     * each knight once. The foe's level is only chosen once there is a foe.
     */
    private static final ScorePad CASTLE = ScorePad.cooperative(
            1,
            4,
            List.of(),
            List.of(
                    new Variant.Choice("foe", List.of("none", "dragon", "giant")),
                    new Variant.Range("level", 0, 6).onlyWhen("foe", "dragon")),
            List.of(ScoreCategory.of("walls"), ScoreCategory.of("towers")),
            List.of(PlayerSetupChoice.of("knight", List.of("red", "blue", "green")).eachOptionOncePerPlay()),
            RecordingMode.WINNER_ONLY);

    private final GameModule module = new CooperativeGameModule() {
        @Override
        public UUID id() {
            return UUID.fromString("00000000-0000-7000-8000-000000000002");
        }

        @Override
        public String key() {
            return "castle";
        }

        @Override
        public ScorePad scorePad() {
            return CASTLE;
        }

        @Override
        protected int groupScore(Map<String, String> setup, boolean won, Map<String, Integer> rows, int players) {
            int level = Integer.parseInt(scorePad().valueOf("level", setup));
            return (won ? 10 * rows.get("walls") + 5 * level : 0) - rows.get("towers");
        }
    };

    private static final Map<String, String> DRAGON_3 = Map.of("foe", "dragon", "level", "3");

    @Test
    void recordsWhetherTheGroupWonWithoutAScore() {
        assertThat(module.score(entry(RecordingMode.WINNER_ONLY, DRAGON_3, new GroupEntry(true, Map.of()), knight("red"))))
                .isEqualTo(new Outcome.Cooperative(true, null));
    }

    @Test
    void worksOutTheGroupsScoreFromItsRows() {
        GroupEntry won = new GroupEntry(true, Map.of("walls", 4, "towers", 2));
        GroupEntry lost = new GroupEntry(false, Map.of("walls", 4, "towers", 2));

        assertThat(module.score(entry(RecordingMode.POINTS, DRAGON_3, won, knight("red"), knight("blue"))))
                .isEqualTo(new Outcome.Cooperative(true, 53));
        assertThat(module.score(entry(RecordingMode.POINTS, DRAGON_3, lost, knight("red"))))
                .isEqualTo(new Outcome.Cooperative(false, -2));
    }

    @Test
    void needsEveryRowWithPointsAndNoneWithout() {
        assertRefused(
                Standard.SCORE_MISSING,
                entry(RecordingMode.POINTS, DRAGON_3, new GroupEntry(true, Map.of("walls", 4)), knight("red")));
        assertRefused(
                Standard.SCORE_NOT_ALLOWED,
                entry(RecordingMode.POINTS, DRAGON_3, new GroupEntry(true, Map.of("walls", -1, "towers", 0)), knight("red")));
        assertRefused(
                Standard.NOT_RECORDED,
                entry(RecordingMode.WINNER_ONLY, DRAGON_3, new GroupEntry(true, Map.of("walls", 4, "towers", 2)), knight("red")));
    }

    @Test
    void needsTheGroupsResultAndNothingForPlayersOneByOne() {
        assertRefused(Standard.GROUP_RESULT_MISSING, entry(RecordingMode.WINNER_ONLY, DRAGON_3, null, knight("red")));
        PlayEntry scoredPlayer = new PlayEntry(
                RecordingMode.WINNER_ONLY,
                DRAGON_3,
                List.of(new PlayerEntry(Map.of("walls", 3), false)),
                new GroupEntry(true, Map.of()));
        assertRefused(Standard.NOT_RECORDED, scoredPlayer);
        PlayEntry markedWinner = new PlayEntry(
                RecordingMode.WINNER_ONLY, DRAGON_3, List.of(new PlayerEntry(Map.of(), true)), new GroupEntry(true, Map.of()));
        assertRefused(Standard.NOT_RECORDED, markedWinner);
    }

    @Test
    void checksThePlayersAndTheirPlayerSetup() {
        GroupEntry won = new GroupEntry(true, Map.of());

        assertRefused(Standard.TOO_FEW_PLAYERS, entry(RecordingMode.WINNER_ONLY, DRAGON_3, won));
        assertRefused(
                Standard.TOO_MANY_PLAYERS,
                entry(RecordingMode.WINNER_ONLY, DRAGON_3, won, knight("red"), knight("blue"), knight("green"), nobody(), nobody()));
        assertRefused(Standard.CHOSEN_TWICE, entry(RecordingMode.WINNER_ONLY, DRAGON_3, won, knight("red"), knight("red")));
    }

    @Test
    void aVariantThatDependsOnTheSetupIsOnlyChosenWhereItHolds() {
        GroupEntry won = new GroupEntry(true, Map.of());

        assertThat(CASTLE.variantsFor(Map.of("foe", "giant"), false)).extracting(Variant::key).containsExactly("foe");
        assertThat(CASTLE.variantsFor(DRAGON_3, false)).extracting(Variant::key).containsExactly("foe", "level");
        assertRefused(
                Standard.NOT_RECORDED,
                entry(RecordingMode.WINNER_ONLY, Map.of("foe", "giant", "level", "3"), won, knight("red")));
        assertRefused(
                Standard.NOT_RECORDED,
                entry(RecordingMode.WINNER_ONLY, Map.of("foe", "dragon", "level", "7"), won, knight("red")));
        assertThat(module.score(entry(RecordingMode.WINNER_ONLY, Map.of("foe", "giant"), won, knight("red"))))
                .isEqualTo(new Outcome.Cooperative(true, null));
    }

    @Test
    void aNumberVariantIsAWholeNumberInItsRange() {
        Variant.Range level = new Variant.Range("level", 0, 6);

        assertThat(level.defaultValue()).isEqualTo("0");
        assertThat(level.allows("0")).isTrue();
        assertThat(level.allows("6")).isTrue();
        assertThat(level.allows("7")).isFalse();
        assertThat(level.allows("-1")).isFalse();
        assertThat(level.allows("two")).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> new Variant.Range("level", 3, 3));
        assertThatIllegalArgumentException().isThrownBy(() -> new Variant.Range("level", 0, 6, 9, false, null));
    }

    @Test
    void aCooperativePadHasNoTieBreakersSoloPlaysOrVictoryTypes() {
        List<ScoreCategory> walls = List.of(ScoreCategory.of("walls"));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ScorePad(
                        1, 4, List.of(), walls, List.of(TieBreaker.most("walls")), false, null, List.of(), List.of(),
                        List.of(), true, RecordingMode.POINTS));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ScorePad(
                        1, 4, List.of(), walls, List.of(), false, Solo.forScoreOnly(), List.of(), List.of(), List.of(),
                        true, RecordingMode.POINTS));
        // A Variant can't depend on itself, or on something that isn't on the pad.
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ScorePad.cooperative(
                        1, 4, List.of(), List.of(new Variant.Range("level", 0, 6).onlyWhen("level", "1")), walls, List.of(),
                        RecordingMode.POINTS));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ScorePad.cooperative(
                        1, 4, List.of(), List.of(new Variant.Range("level", 0, 6).onlyWhen("foe", "dragon")), walls,
                        List.of(), RecordingMode.POINTS));
    }

    @Test
    void needsACooperativePad() {
        GameModule wrong = new CooperativeGameModule() {
            @Override
            public UUID id() {
                return UUID.fromString("00000000-0000-7000-8000-000000000003");
            }

            @Override
            public String key() {
                return "wrong";
            }

            @Override
            public ScorePad scorePad() {
                return new ScorePad(2, 4, List.of(), List.of(ScoreCategory.of("walls")), List.of(), false);
            }

            @Override
            protected int groupScore(Map<String, String> setup, boolean won, Map<String, Integer> rows, int players) {
                return 0;
            }
        };

        assertThatIllegalStateException()
                .isThrownBy(() -> wrong.score(entry(RecordingMode.WINNER_ONLY, Map.of(), new GroupEntry(true, Map.of()), nobody())));
    }

    private void assertRefused(Standard reason, PlayEntry entry) {
        InvalidPlayException expected = new InvalidPlayException(reason);
        assertThatExceptionOfType(InvalidPlayException.class)
                .isThrownBy(() -> module.score(entry))
                .extracting(InvalidPlayException::reason)
                .isEqualTo(expected.reason());
    }

    private static PlayEntry entry(
            RecordingMode recordingMode, Map<String, String> setup, GroupEntry group, PlayerEntry... players) {
        return new PlayEntry(recordingMode, setup, List.of(players), group);
    }

    private static PlayerEntry knight(String knight) {
        return new PlayerEntry(Map.of(), false, Map.of("knight", knight));
    }

    private static PlayerEntry nobody() {
        return new PlayerEntry(Map.of(), false);
    }
}
