package io.github.oliverjuhasz.boardkeeper.gamemodules.spiritisland;

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
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

class SpiritIslandGameModuleTests {

    /** Against England at level 3, which is Difficulty 6. */
    private static final Map<String, String> ENGLAND_3 = Map.of("adversary", "england", "englandLevel", "3");

    private final SpiritIslandGameModule module = new SpiritIslandGameModule();

    @Test
    void isFoundThroughTheServiceLoader() {
        assertThat(ServiceLoader.load(GameModule.class)).extracting(GameModule::id).contains(SpiritIslandGameModule.ID);
    }

    @Test
    void aPlayStartsWithOnlyWhetherTheSpiritsWon() {
        assertThat(module.scorePad().usualRecordingMode()).isEqualTo(RecordingMode.WINNER_ONLY);
        assertThat(module.score(onlyWhether(true, ENGLAND_3, spirit("thunderspeaker"))))
                .isEqualTo(new Outcome.Cooperative(true, null));
        assertThat(module.score(onlyWhether(false, Map.of(), spirit("thunderspeaker"))))
                .isEqualTo(new Outcome.Cooperative(false, null));
    }

    /** Page 23: "Score 5x Difficulty, +10 bonus for winning, +2 per Invader Card remaining in the deck." */
    @Test
    void scoresAVictory() {
        // 5 × 6 + 10 + 2 × 4, + 7 Dahan for two players (3) − 5 Blight for two players (2).
        assertThat(module.score(scored(true, ENGLAND_3, 4, 7, 5, spirit("thunderspeaker"), spirit("oceansHungryGrasp"))))
                .isEqualTo(new Outcome.Cooperative(true, 49));
    }

    /** Page 23: "Score 2x Difficulty, +1 per Invader Card not in the deck." */
    @Test
    void scoresADefeat() {
        // 2 × 6 + 9, + 3 − 2.
        assertThat(module.score(scored(false, ENGLAND_3, 9, 7, 5, spirit("thunderspeaker"), spirit("oceansHungryGrasp"))))
                .isEqualTo(new Outcome.Cooperative(false, 22));
    }

    /** "+1 per X living Dahan and -1 per X Blight": only complete groups of X count. */
    @Test
    void countsDahanAndBlightInCompleteGroupsOfThePlayers() {
        Map<String, String> noDifficulty = Map.of();
        // Alone, each one counts: 10 + 0 + 7 − 5.
        assertThat(module.score(scored(true, noDifficulty, 0, 7, 5, spirit("thunderspeaker"))))
                .isEqualTo(new Outcome.Cooperative(true, 12));
        // Three players: 7 Dahan make 2 groups, 5 Blight make 1.
        assertThat(module.score(scored(
                        true, noDifficulty, 0, 7, 5, spirit("thunderspeaker"), spirit("riverSurgesInSunlight"), anySpirit())))
                .isEqualTo(new Outcome.Cooperative(true, 11));
        // Four players: 3 Dahan make no group, 8 Blight make 2. A score can be below zero.
        assertThat(module.score(scored(false, noDifficulty, 0, 3, 8, anySpirit(), anySpirit(), anySpirit(), anySpirit())))
                .isEqualTo(new Outcome.Cooperative(false, -2));
    }

    /** Page 28: the Difficulty of each Adversary at each level, of each Scenario, and of the thematic map. */
    @Test
    void worksOutTheDifficultyFromTheChart() {
        assertThat(levels("brandenburgPrussia")).containsExactly(1, 2, 4, 6, 7, 9, 10);
        assertThat(levels("england")).containsExactly(1, 3, 4, 6, 7, 9, 10);
        assertThat(levels("sweden")).containsExactly(1, 2, 3, 5, 6, 7, 8);
        assertThat(SpiritIslandGameModule.difficulty(Map.of())).isZero();
        assertThat(SpiritIslandGameModule.difficulty(Map.of("scenario", "blitz"))).isZero();
        assertThat(SpiritIslandGameModule.difficulty(Map.of("scenario", "guardTheIslesHeart"))).isZero();
        assertThat(SpiritIslandGameModule.difficulty(Map.of("scenario", "ritualsOfTerror"))).isEqualTo(3);
        assertThat(SpiritIslandGameModule.difficulty(Map.of("scenario", "dahanInsurrection"))).isEqualTo(4);
        assertThat(SpiritIslandGameModule.difficulty(Map.of("thematicMap", "true"))).isEqualTo(3);
        // "Add the Difficulty of a Scenario and Adversary together."
        assertThat(SpiritIslandGameModule.difficulty(
                        Map.of("adversary", "sweden", "swedenLevel", "6", "scenario", "dahanInsurrection", "thematicMap", "true")))
                .isEqualTo(15);
    }

    @Test
    void anAdversarysLevelIsChosenOnlyWithThatAdversary() {
        assertThat(module.scorePad().variantsFor(ENGLAND_3, false))
                .extracting(Variant::key)
                .containsExactly("adversary", "englandLevel", "scenario", "thematicMap");
        assertRefused("notRecorded", onlyWhether(true, Map.of("englandLevel", "3"), spirit("thunderspeaker")));
        assertRefused(
                "notRecorded",
                onlyWhether(true, Map.of("adversary", "sweden", "englandLevel", "3"), spirit("thunderspeaker")));
        assertRefused(
                "notRecorded",
                onlyWhether(true, Map.of("adversary", "england", "englandLevel", "7"), spirit("thunderspeaker")));
    }

    @Test
    void eachSpiritIsPlayedByOnePlayerAtMost() {
        assertRefused("chosenTwice", onlyWhether(true, Map.of(), spirit("thunderspeaker"), spirit("thunderspeaker")));
        assertRefused("notRecorded", onlyWhether(true, Map.of(), spirit("finder")));
        assertThat(module.scorePad().playerSetup().getFirst().options()).hasSize(8);
    }

    @Test
    void isForOneToFourPlayers() {
        assertRefused("tooFewPlayers", onlyWhether(true, Map.of()));
        assertRefused("tooManyPlayers", onlyWhether(true, Map.of(), anySpirit(), anySpirit(), anySpirit(), anySpirit(), anySpirit()));
    }

    @Test
    void needsEveryRowForAScore() {
        PlayEntry withoutBlight = new PlayEntry(
                RecordingMode.POINTS,
                Map.of(),
                List.of(spirit("thunderspeaker")),
                new GroupEntry(true, Map.of("invaderCards", 4, "dahan", 7)));
        assertRefused("scoreMissing", withoutBlight);
    }

    /** Everything the pad names has a text in English, and Dutch and Hungarian have the same texts. */
    @Test
    void hasATextForEverythingOnItsPadInEveryLanguage() throws Exception {
        String prefix = SpiritIslandGameModule.KEY + ".";
        Properties english = texts("en");
        assertThat(english).containsKey(prefix + "name");
        for (Variant variant : module.scorePad().variants()) {
            assertThat(english).containsKey(prefix + "variant." + variant.key());
            if (variant instanceof Variant.Choice choice) {
                for (String option : choice.options()) {
                    assertThat(english).containsKey(prefix + "variant." + variant.key() + "." + option);
                }
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

    private static List<Integer> levels(String adversary) {
        return java.util.stream.IntStream.rangeClosed(0, 6)
                .mapToObj(level -> SpiritIslandGameModule.difficulty(
                        Map.of("adversary", adversary, adversary + "Level", String.valueOf(level))))
                .toList();
    }

    private Properties texts(String language) throws Exception {
        Properties texts = new Properties();
        try (var file = getClass().getResourceAsStream("/i18n/spirit-island_" + language + ".properties")) {
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

    private static PlayEntry onlyWhether(boolean won, Map<String, String> setup, PlayerEntry... players) {
        return new PlayEntry(RecordingMode.WINNER_ONLY, setup, Arrays.asList(players), new GroupEntry(won, Map.of()));
    }

    private static PlayEntry scored(
            boolean won, Map<String, String> setup, int invaderCards, int dahan, int blight, PlayerEntry... players) {
        return new PlayEntry(
                RecordingMode.POINTS,
                setup,
                Arrays.asList(players),
                new GroupEntry(won, Map.of("invaderCards", invaderCards, "dahan", dahan, "blight", blight)));
    }

    private static PlayerEntry spirit(String spirit) {
        return new PlayerEntry(Map.of(), false, Map.of("spirit", spirit));
    }

    /** A Player whose Spirit nobody wrote down. */
    private static PlayerEntry anySpirit() {
        return new PlayerEntry(Map.of(), false);
    }
}
