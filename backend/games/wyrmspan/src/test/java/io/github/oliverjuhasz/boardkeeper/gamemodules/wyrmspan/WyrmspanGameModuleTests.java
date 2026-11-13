package io.github.oliverjuhasz.boardkeeper.gamemodules.wyrmspan;

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

class WyrmspanGameModuleTests {

    private static final Map<String, String> NO_SETUP = Map.of();
    private static final Map<String, String> LEVEL_3 = Map.of("automaLevel", "3");

    private final WyrmspanGameModule module = new WyrmspanGameModule();

    @Test
    void isFoundThroughTheServiceLoader() {
        assertThat(ServiceLoader.load(GameModule.class)).extracting(GameModule::id).contains(WyrmspanGameModule.ID);
    }

    /** Every row of the score pad counts, and the most points win. */
    @Test
    void addsUpTheEightRowsOfTheScorePad() {
        assertThat(results(NO_SETUP, pad(12, 40, 6, 9, 7, 5, 11, 3), pad(5, 52, 0, 11, 2, 9, 8, 1), pad(0, 30, 3, 4, 4, 2, 5, 2)))
                .containsExactly(
                        new PlayerResult(93, 1, true), new PlayerResult(88, 2, false), new PlayerResult(50, 3, false));
    }

    /** The dragons that decide a tie are not on the pad. */
    @Test
    void aTieForFirstPlaceIsLeftToTheRecorder() {
        Outcome.Competitive tied = score(NO_SETUP, pad(12, 40, 6, 9, 7, 5, 11, 3), pad(5, 52, 1, 11, 2, 9, 8, 5));

        assertThat(tied.results()).extracting(PlayerResult::placing).containsExactly(1, 1);
        assertThat(tied.tieForRecorder()).containsExactly(0, 1);
    }

    /** Page 5 of the Automa rulebook: the Automa's score is worked out on its own. */
    @Test
    void aSoloPlayIsWonByBeatingTheAutomasScore() {
        assertThat(results(NO_SETUP, solo(pad(12, 40, 6, 9, 7, 5, 11, 3), 85)))
                .containsExactly(new PlayerResult(93, null, true));
        assertThat(results(LEVEL_3, solo(pad(12, 40, 6, 9, 7, 5, 11, 3), 101)))
                .containsExactly(new PlayerResult(93, null, false));
    }

    /** "In the event of a tie, you win!" */
    @Test
    void aSoloPlayTiedWithTheAutomaIsWon() {
        assertThat(results(NO_SETUP, solo(pad(12, 40, 6, 9, 7, 5, 11, 3), 93)))
                .containsExactly(new PlayerResult(93, null, true));
    }

    @Test
    void theAutomaBelongsToSoloPlaysOnly() {
        // Its score has to be entered alone...
        assertRefused("scoreMissing", entry(NO_SETUP, pad(12, 40, 6, 9, 7, 5, 11, 3)));
        // ...and only then.
        PlayerEntry withAutoma = solo(pad(12, 40, 6, 9, 7, 5, 11, 3), 85);
        assertRefused("notRecorded", entry(NO_SETUP, withAutoma, pad(5, 52, 0, 11, 2, 9, 8, 1)));
        // The same goes for its level and Ravel Mode.
        assertRefused("notRecorded", entry(LEVEL_3, pad(12, 40, 6, 9, 7, 5, 11, 3), pad(5, 52, 0, 11, 2, 9, 8, 1)));
        assertThat(results(Map.of("automaLevel", "2", "ravelMode", "true"), withAutoma)).hasSize(1);
        assertRefused("notRecorded", entry(Map.of("automaLevel", "4"), withAutoma));
    }

    @Test
    void aSoloPlayCanBeRecordedWithOnlyWhetherItWasWon() {
        PlayEntry won = new PlayEntry(RecordingMode.WINNER_ONLY, LEVEL_3, List.of(new PlayerEntry(Map.of(), true)), null);

        assertThat(((Outcome.Competitive) module.score(won)).results())
                .containsExactly(new PlayerResult(null, null, true));
    }

    @Test
    void isRecordedForOneToFivePlayers() {
        PlayerEntry anyone = pad(1, 1, 1, 1, 1, 1, 1, 1);

        assertRefused("tooFewPlayers", entry(NO_SETUP));
        assertThat(results(NO_SETUP, anyone, anyone, anyone, anyone, anyone)).hasSize(5);
        assertRefused("tooManyPlayers", entry(NO_SETUP, anyone, anyone, anyone, anyone, anyone, anyone));
    }

    /** Everything the pad names has a text in English, and Dutch and Hungarian have the same texts. */
    @Test
    void hasATextForEverythingOnItsPadInEveryLanguage() throws Exception {
        Properties english = texts("en");
        assertThat(english).containsKeys("wyrmspan.name", "wyrmspan.tie");
        for (Variant variant : module.scorePad().variants()) {
            assertThat(english).containsKey("wyrmspan.variant." + variant.key());
            if (variant instanceof Variant.Choice choice) {
                for (String option : choice.options()) {
                    assertThat(english).containsKey("wyrmspan.variant." + variant.key() + "." + option);
                }
            }
        }
        for (ScoreCategory category : module.scorePad().categories()) {
            assertThat(english).containsKey("wyrmspan.category." + category.key());
        }
        assertThat(texts("nl").keySet()).isEqualTo(english.keySet());
        assertThat(texts("hu").keySet()).isEqualTo(english.keySet());
    }

    private Properties texts(String language) throws Exception {
        Properties texts = new Properties();
        try (var file = getClass().getResourceAsStream("/i18n/wyrmspan_" + language + ".properties")) {
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

    /** A Player's column of the score pad, row by row. */
    private static PlayerEntry pad(
            int dragonGuild,
            int printedDragons,
            int endGameAbilities,
            int eggs,
            int cachedResources,
            int tuckedCards,
            int publicObjectives,
            int coinsAndItems) {
        Map<String, Integer> scores = new HashMap<>();
        scores.put("dragonGuild", dragonGuild);
        scores.put("printedDragons", printedDragons);
        scores.put("endGameAbilities", endGameAbilities);
        scores.put("eggs", eggs);
        scores.put("cachedResources", cachedResources);
        scores.put("tuckedCards", tuckedCards);
        scores.put("publicObjectives", publicObjectives);
        scores.put("coinsAndItems", coinsAndItems);
        return new PlayerEntry(scores, false);
    }

    /** The pad of a Solo Play, with the Automa's score. */
    private static PlayerEntry solo(PlayerEntry player, int automa) {
        Map<String, Integer> scores = new HashMap<>(player.scores());
        scores.put("automa", automa);
        return new PlayerEntry(scores, false);
    }
}
