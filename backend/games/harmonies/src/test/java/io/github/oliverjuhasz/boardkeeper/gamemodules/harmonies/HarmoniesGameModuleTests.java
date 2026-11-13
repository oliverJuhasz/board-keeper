package io.github.oliverjuhasz.boardkeeper.gamemodules.harmonies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.github.oliverjuhasz.boardkeeper.gameapi.GameModule;
import io.github.oliverjuhasz.boardkeeper.gameapi.InvalidPlayException;
import io.github.oliverjuhasz.boardkeeper.gameapi.Outcome;
import io.github.oliverjuhasz.boardkeeper.gameapi.PlayEntry;
import io.github.oliverjuhasz.boardkeeper.gameapi.PlayerEntry;
import io.github.oliverjuhasz.boardkeeper.gameapi.PlayerResult;
import io.github.oliverjuhasz.boardkeeper.gameapi.RecordingMode;
import io.github.oliverjuhasz.boardkeeper.gameapi.ScoreCategory;
import io.github.oliverjuhasz.boardkeeper.gameapi.Variant;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

class HarmoniesGameModuleTests {

    private static final Map<String, String> SIDE_A = Map.of();
    private static final Map<String, String> SIDE_B = Map.of("boardSide", "b");
    private static final Map<String, String> WITH_SPIRITS = Map.of("naturesSpirit", "true");

    private final HarmoniesGameModule module = new HarmoniesGameModule();

    @Test
    void isFoundThroughTheServiceLoader() {
        assertThat(ServiceLoader.load(GameModule.class)).extracting(GameModule::id).contains(HarmoniesGameModule.ID);
    }

    /**
     * The example on page 9 of the rulebook: Pauline scores 4, 10, 10, 10 and 19 for her
     * Landscapes, which is 53, and 63 for her Animal cards, for a total of 116.
     */
    @Test
    void addsUpLandscapesAndAnimalCardsAsInTheRulebooksExample() {
        assertThat(results(SIDE_A, sideA(4, 10, 10, 10, 19, 63), sideA(7, 4, 5, 0, 8, 40)))
                .containsExactly(new PlayerResult(116, 1, true), new PlayerResult(64, 2, false));
    }

    /** The example on page 11: the same Landscapes, with 14 points from the Nature's Spirit card. */
    @Test
    void aNaturesSpiritCardScoresOnTopOfEverythingElse() {
        PlayerEntry pauline = with(sideA(4, 10, 10, 10, 19, 63), "naturesSpirit", 14);
        PlayerEntry johan = with(sideA(7, 4, 5, 0, 8, 40), "naturesSpirit", 0);

        assertThat(results(WITH_SPIRITS, pauline, johan)).extracting(PlayerResult::total).containsExactly(130, 64);
        // Without the cards there is no such row...
        assertRefused("notRecorded", entry(SIDE_A, pauline, sideA(7, 4, 5, 0, 8, 40)));
        // ...and with them every Player has one.
        assertRefused("scoreMissing", entry(WITH_SPIRITS, pauline, sideA(7, 4, 5, 0, 8, 40)));
    }

    @Test
    void sideBScoresIslandsInsteadOfTheRiver() {
        assertThat(results(SIDE_B, sideB(4, 10, 10, 10, 20, 63), sideB(7, 4, 5, 0, 5, 40)))
                .extracting(PlayerResult::total)
                .containsExactly(117, 61);
        // A river belongs to side A, and islands to side B.
        assertRefused("notRecorded", entry(SIDE_B, sideA(4, 10, 10, 10, 19, 63), sideB(7, 4, 5, 0, 5, 40)));
        assertRefused("notRecorded", entry(SIDE_A, sideB(4, 10, 10, 10, 20, 63), sideA(7, 4, 5, 0, 8, 40)));
    }

    @Test
    void anIslandIsWorthFivePointsAndThereIsAlwaysOne() {
        assertRefused("scoreNotAllowed", entry(SIDE_B, sideB(4, 10, 10, 10, 12, 63), sideB(7, 4, 5, 0, 5, 40)));
        assertRefused("scoreNotAllowed", entry(SIDE_B, sideB(4, 10, 10, 10, 0, 63), sideB(7, 4, 5, 0, 5, 40)));
    }

    @Test
    void fieldsAndBuildingsAreWorthFivePointsEach() {
        assertRefused("scoreNotAllowed", entry(SIDE_A, sideA(4, 10, 12, 10, 19, 63), sideA(7, 4, 5, 0, 8, 40)));
        assertRefused("scoreNotAllowed", entry(SIDE_A, sideA(4, 10, 10, 3, 19, 63), sideA(7, 4, 5, 0, 8, 40)));
        // A river can have any length, and so any of the scores for one.
        assertThat(results(SIDE_A, sideA(0, 0, 0, 0, 2, 0), sideA(0, 0, 0, 0, 11, 0)))
                .extracting(PlayerResult::placing)
                .containsExactly(2, 1);
    }

    /** Animal cubes decide a tie, and the pad does not say how many each Player placed. */
    @Test
    void aTieForFirstPlaceIsLeftToTheRecorder() {
        Outcome.Competitive tied = score(SIDE_A, sideA(4, 10, 10, 10, 19, 63), sideA(7, 10, 10, 10, 19, 60));

        assertThat(tied.results()).extracting(PlayerResult::placing).containsExactly(1, 1);
        assertThat(tied.tieForRecorder()).containsExactly(0, 1);
    }

    @Test
    void isRecordedForOneToFourPlayers() {
        PlayerEntry anyone = sideA(1, 1, 5, 5, 2, 4);

        assertRefused("tooFewPlayers", entry(SIDE_A));
        assertRefused("tooManyPlayers", entry(SIDE_A, anyone, anyone, anyone, anyone, anyone));
    }

    /** In the solo mode on page 11 a Player earns suns for their score instead of winning. */
    @Test
    void aSoloPlayIsRecordedForScoreOnly() {
        assertThat(results(WITH_SPIRITS, with(sideA(4, 10, 10, 10, 19, 63), "naturesSpirit", 14)))
                .containsExactly(new PlayerResult(130, null, null));
    }

    /** Everything the pad names has a text in English, and Dutch and Hungarian have the same texts. */
    @Test
    void hasATextForEverythingOnItsPadInEveryLanguage() throws Exception {
        Properties english = texts("en");
        assertThat(english).containsKeys("harmonies.name", "harmonies.tie");
        for (Variant variant : module.scorePad().variants()) {
            assertThat(english).containsKey("harmonies.variant." + variant.key());
            if (variant instanceof Variant.Choice choice) {
                for (String option : choice.options()) {
                    assertThat(english).containsKey("harmonies.variant." + variant.key() + "." + option);
                }
            }
        }
        for (ScoreCategory category : module.scorePad().categories()) {
            assertThat(english).containsKey("harmonies.category." + category.key());
        }
        assertThat(texts("nl").keySet()).isEqualTo(english.keySet());
        assertThat(texts("hu").keySet()).isEqualTo(english.keySet());
    }

    private Properties texts(String language) throws Exception {
        Properties texts = new Properties();
        try (var file = getClass().getResourceAsStream("/i18n/harmonies_" + language + ".properties")) {
            texts.load(new InputStreamReader(file, StandardCharsets.UTF_8));
        }
        return texts;
    }

    private Outcome.Competitive score(Map<String, String> setup, PlayerEntry... players) {
        return (Outcome.Competitive) module.score(entry(setup, players));
    }

    private List<PlayerResult> results(Map<String, String> setup, PlayerEntry... players) {
        return score(setup, players).results();
    }

    private void assertRefused(String reason, PlayEntry entry) {
        assertThatExceptionOfType(InvalidPlayException.class)
                .isThrownBy(() -> module.score(entry))
                .extracting(InvalidPlayException::reason)
                .isEqualTo(reason);
    }

    private static PlayEntry entry(Map<String, String> setup, PlayerEntry... players) {
        return new PlayEntry(RecordingMode.POINTS, setup, Arrays.asList(players), null);
    }

    /** A Player's pad on side A of the Personal boards, where Water is the River. */
    private static PlayerEntry sideA(int trees, int mountains, int fields, int buildings, int river, int animals) {
        return new PlayerEntry(
                Map.of("trees", trees, "mountains", mountains, "fields", fields, "buildings", buildings, "river", river, "animals", animals),
                false);
    }

    /** A Player's pad on side B of the Personal boards, where Water is the Islands. */
    private static PlayerEntry sideB(int trees, int mountains, int fields, int buildings, int islands, int animals) {
        return new PlayerEntry(
                Map.of("trees", trees, "mountains", mountains, "fields", fields, "buildings", buildings, "islands", islands, "animals", animals),
                false);
    }

    private static PlayerEntry with(PlayerEntry player, String category, int value) {
        Map<String, Integer> scores = new HashMap<>(player.scores());
        scores.put(category, value);
        return new PlayerEntry(scores, player.winner());
    }
}
