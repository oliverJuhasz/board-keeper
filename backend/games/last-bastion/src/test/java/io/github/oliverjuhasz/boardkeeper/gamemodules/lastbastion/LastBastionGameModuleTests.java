package io.github.oliverjuhasz.boardkeeper.gamemodules.lastbastion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.github.oliverjuhasz.boardkeeper.gameapi.GameModule;
import io.github.oliverjuhasz.boardkeeper.gameapi.GroupEntry;
import io.github.oliverjuhasz.boardkeeper.gameapi.InvalidPlayException;
import io.github.oliverjuhasz.boardkeeper.gameapi.Outcome;
import io.github.oliverjuhasz.boardkeeper.gameapi.PlayEntry;
import io.github.oliverjuhasz.boardkeeper.gameapi.PlayerEntry;
import io.github.oliverjuhasz.boardkeeper.gameapi.PlayerSetupChoice;
import io.github.oliverjuhasz.boardkeeper.gameapi.RecordingMode;
import io.github.oliverjuhasz.boardkeeper.gameapi.ScoreCategory;
import io.github.oliverjuhasz.boardkeeper.gameapi.Variant;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

class LastBastionGameModuleTests {

    private static final Map<String, String> NORMAL = Map.of("difficulty", "normal");

    private final LastBastionGameModule module = new LastBastionGameModule();

    @Test
    void isFoundThroughTheServiceLoader() {
        assertThat(ServiceLoader.load(GameModule.class)).extracting(GameModule::id).contains(LastBastionGameModule.ID);
    }

    /** The box has a pad of score sheets, so a Play starts in points. */
    @Test
    void aPlayStartsInPoints() {
        assertThat(module.scorePad().usualRecordingMode()).isEqualTo(RecordingMode.POINTS);
        assertThat(module.scorePad().cooperative()).isTrue();
    }

    /**
     * Page 10: "10 points for a victory in Normal mode", "+ 1 point per Life token", "+ 2 points
     * per Call to Arms token", "- 3 points per dead Hero", "- 4 points per Bastion tile in the
     * Grasp of Evil".
     */
    @Test
    void scoresAVictory() {
        // 10 + 6 + 2 × 2 − 3 × 1 − 4 × 2.
        assertThat(module.score(won(NORMAL, 6, 2, 1, 2, hero("kazgin"), hero("sindara"), hero("teagaan"), hero("fengLi"))))
                .isEqualTo(new Outcome.Cooperative(true, 9));
    }

    /** Page 10: 5, 10, 15 or 20 points for a victory in Novice, Normal, Difficult and Heroic mode. */
    @Test
    void scoresAVictoryByItsDifficulty() {
        for (var level : Map.of("novice", 5, "normal", 10, "difficult", 15, "heroic", 20).entrySet()) {
            assertThat(module.score(won(Map.of("difficulty", level.getKey()), 0, 0, 0, 0, hero("kazgin"))))
                    .isEqualTo(new Outcome.Cooperative(true, level.getValue()));
        }
    }

    /** The rulebook suggests Normal for the first games: it is the default. */
    @Test
    void isPlayedInNormalModeUnlessSaidOtherwise() {
        assertThat(module.scorePad().valueOf("difficulty", Map.of())).isEqualTo("normal");
        assertThat(module.score(won(Map.of(), 3, 1, 0, 0, hero("kazgin"))))
                .isEqualTo(new Outcome.Cooperative(true, 15));
        assertThat(module.scorePad().variants())
                .singleElement()
                .isInstanceOfSatisfying(Variant.Choice.class, difficulty -> assertThat(difficulty.options())
                        .containsExactly("novice", "normal", "difficult", "heroic"));
    }

    /** Only a victory is scored: a defeat has no rows and no score. */
    @Test
    void aDefeatHasNoScore() {
        assertThat(module.scorePad().groupCategoriesFor(NORMAL, false)).isEmpty();
        assertThat(module.score(entry(RecordingMode.POINTS, NORMAL, new GroupEntry(false, Map.of()), hero("kazgin"))))
                .isEqualTo(new Outcome.Cooperative(false, null));
        assertRefused(
                "notRecorded",
                entry(RecordingMode.POINTS, NORMAL, new GroupEntry(false, rows(6, 2, 1, 3)), hero("kazgin"), hero("sindara")));
    }

    @Test
    void canBeRecordedWithOnlyWhetherTheHeroesWon() {
        assertThat(module.score(entry(RecordingMode.WINNER_ONLY, NORMAL, new GroupEntry(true, Map.of()), hero("kazgin"))))
                .isEqualTo(new Outcome.Cooperative(true, null));
    }

    /** Page 10: "A third Bastion tile falls under the Grasp of Evil" is a defeat. */
    @Test
    void aVictoryLeavesAtMostTwoTilesInTheGraspOfEvil() {
        assertThat(module.score(won(NORMAL, 0, 0, 0, 2, hero("kazgin")))).isEqualTo(new Outcome.Cooperative(true, 2));
        assertRefused("scoreNotAllowed", won(NORMAL, 0, 0, 0, 3, hero("kazgin")));
    }

    /** Page 10: "All of the heroes are taken out" is a defeat, and a dead Hero doesn't come back. */
    @Test
    void aVictoryLeavesAtLeastOneHeroAlive() {
        assertRefused("tooManyDeadHeroes", won(NORMAL, 3, 0, 1, 0, hero("kazgin")));
        assertRefused("tooManyDeadHeroes", won(NORMAL, 3, 0, 2, 0, hero("kazgin"), hero("sindara")));
        assertThat(module.score(won(NORMAL, 3, 0, 1, 0, hero("kazgin"), hero("sindara"))))
                .isEqualTo(new Outcome.Cooperative(true, 10));
        assertThat(module.score(won(NORMAL, 3, 0, 3, 0, hero("kazgin"), hero("sindara"), hero("teagaan"), hero("mylfaar"))))
                .isEqualTo(new Outcome.Cooperative(true, 4));
    }

    /** Page 2 and page 11: one to four players, each with a Hero of their own. */
    @Test
    void isForOneToFourPlayersEachWithAHeroOfTheirOwn() {
        GroupEntry victory = new GroupEntry(true, Map.of());

        assertRefused("tooFewPlayers", entry(RecordingMode.WINNER_ONLY, NORMAL, victory));
        assertRefused(
                "tooManyPlayers",
                entry(RecordingMode.WINNER_ONLY, NORMAL, victory, anyHero(), anyHero(), anyHero(), anyHero(), anyHero()));
        assertRefused("chosenTwice", entry(RecordingMode.WINNER_ONLY, NORMAL, victory, hero("kazgin"), hero("kazgin")));
        assertThat(module.scorePad().playerSetup().getFirst().options())
                .containsExactly(
                        "elmerAndChestnut", "mylfaar", "arneAndSoko", "sindara", "teagaan", "fengLi", "kazgin", "twindalli");
    }

    /** Everything the pad names has a text in English, and Dutch and Hungarian have the same texts. */
    @Test
    void hasATextForEverythingOnItsPadInEveryLanguage() throws Exception {
        String prefix = LastBastionGameModule.KEY + ".";
        Properties english = texts("en");
        assertThat(english).containsKeys(prefix + "name", prefix + "invalid.tooManyDeadHeroes");
        for (Variant variant : module.scorePad().variants()) {
            assertThat(english).containsKey(prefix + "variant." + variant.key());
            for (String option : ((Variant.Choice) variant).options()) {
                assertThat(english).containsKey(prefix + "variant." + variant.key() + "." + option);
            }
        }
        for (ScoreCategory category : module.scorePad().categories()) {
            assertThat(english).containsKey(prefix + "category." + category.key());
        }
        for (PlayerSetupChoice choice : module.scorePad().playerSetup()) {
            assertThat(english).containsKey(prefix + "playerSetup." + choice.key());
            for (String option : choice.options()) {
                assertThat(english).containsKey(prefix + "playerSetup." + choice.key() + "." + option);
            }
        }
        assertThat(texts("nl").keySet()).isEqualTo(english.keySet());
        assertThat(texts("hu").keySet()).isEqualTo(english.keySet());
    }

    private Properties texts(String language) throws Exception {
        Properties texts = new Properties();
        try (var file = getClass().getResourceAsStream("/i18n/last-bastion_" + language + ".properties")) {
            texts.load(new InputStreamReader(file, StandardCharsets.UTF_8));
        }
        return texts;
    }

    private void assertRefused(String reason, PlayEntry entry) {
        assertThatExceptionOfType(InvalidPlayException.class)
                .isThrownBy(() -> module.score(entry))
                .extracting(InvalidPlayException::reason)
                .isEqualTo(reason);
    }

    /** A victory recorded in points, with the group's rows. */
    private static PlayEntry won(
            Map<String, String> setup,
            int lifeTokens,
            int callToArmsTokens,
            int deadHeroes,
            int graspOfEvilTiles,
            PlayerEntry... players) {
        return entry(
                RecordingMode.POINTS,
                setup,
                new GroupEntry(true, rows(lifeTokens, callToArmsTokens, deadHeroes, graspOfEvilTiles)),
                players);
    }

    private static Map<String, Integer> rows(int lifeTokens, int callToArmsTokens, int deadHeroes, int graspOfEvilTiles) {
        return Map.of(
                "lifeTokens", lifeTokens,
                "callToArmsTokens", callToArmsTokens,
                "deadHeroes", deadHeroes,
                "graspOfEvilTiles", graspOfEvilTiles);
    }

    private static PlayEntry entry(
            RecordingMode recordingMode, Map<String, String> setup, GroupEntry group, PlayerEntry... players) {
        return new PlayEntry(recordingMode, setup, Arrays.asList(players), group);
    }

    private static PlayerEntry hero(String hero) {
        return new PlayerEntry(Map.of(), false, Map.of("hero", hero));
    }

    /** A Player whose Hero nobody remembers. */
    private static PlayerEntry anyHero() {
        return new PlayerEntry(Map.of(), false);
    }
}
