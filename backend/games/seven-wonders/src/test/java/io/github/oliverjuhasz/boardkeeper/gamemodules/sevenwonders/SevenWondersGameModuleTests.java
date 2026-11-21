package io.github.oliverjuhasz.boardkeeper.gamemodules.sevenwonders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.github.oliverjuhasz.boardkeeper.gameapi.Expansion;
import io.github.oliverjuhasz.boardkeeper.gameapi.GameModule;
import io.github.oliverjuhasz.boardkeeper.gameapi.InvalidPlayException;
import io.github.oliverjuhasz.boardkeeper.gameapi.Outcome;
import io.github.oliverjuhasz.boardkeeper.gameapi.PlayEntry;
import io.github.oliverjuhasz.boardkeeper.gameapi.PlayerEntry;
import io.github.oliverjuhasz.boardkeeper.gameapi.PlayerResult;
import io.github.oliverjuhasz.boardkeeper.gameapi.PlayerSetupChoice;
import io.github.oliverjuhasz.boardkeeper.gameapi.RecordingMode;
import io.github.oliverjuhasz.boardkeeper.gameapi.ScoreCategory;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

class SevenWondersGameModuleTests {

    private static final Map<String, String> BASE_GAME = Map.of();
    private static final Map<String, String> BOTH = Map.of("leaders", "true", "cities", "true");

    private final SevenWondersGameModule module = new SevenWondersGameModule();

    @Test
    void isFoundThroughTheServiceLoader() {
        assertThat(ServiceLoader.load(GameModule.class)).extracting(GameModule::id).contains(SevenWondersGameModule.ID);
    }

    /** Page 8: the rows of the score pad add up, and the most victory points win. */
    @Test
    void addsUpTheRowsOfTheScorePad() {
        assertThat(results(BASE_GAME, pad(10, 4, 9, 15, 5, 13, 7), pad(7, 2, 5, 20, 8, 1, 4), pad(3, 6, 0, 12, 2, 25, 6)))
                .containsExactly(
                        new PlayerResult(63, 1, true), new PlayerResult(47, 3, false), new PlayerResult(54, 2, false));
    }

    /** "The total may be a negative number" for Military, and Cities' Debts count on the Treasury line. */
    @Test
    void militaryAndTreasuryCanBeNegative() {
        assertThat(results(BASE_GAME, pad(10, -3, -6, 15, 5, 13, 7), pad(7, 2, 5, 20, 8, 1, 4), pad(3, 6, 0, 12, 2, 25, 6)))
                .extracting(PlayerResult::total)
                .containsExactly(41, 47, 54);
        assertRefused("scoreNotAllowed", entry(BASE_GAME, pad(-1, 4, 9, 15, 5, 13, 7), pad(7, 2, 5, 20, 8, 1, 4), pad(3, 6, 0, 12, 2, 25, 6)));
    }

    /** Page 8: the most Treasure wins a tie. The pad doesn't hold the coins, so the recorder decides. */
    @Test
    void aTieForFirstPlaceIsLeftToTheRecorder() {
        Outcome.Competitive tied = score(BASE_GAME, pad(10, 4, 9, 15, 5, 13, 7), pad(7, 2, 5, 20, 8, 1, 4), pad(10, 4, 9, 15, 5, 7, 13));

        assertThat(tied.results()).extracting(PlayerResult::placing).containsExactly(1, 3, 1);
        assertThat(tied.tieForRecorder()).containsExactly(0, 2);
    }

    /** Leaders adds a row for the Leaders, and Cities one for the black cards. */
    @Test
    void theExpansionsAddTheirRows() {
        assertThat(module.scorePad().categoriesFor(BASE_GAME, false))
                .extracting(ScoreCategory::key)
                .containsExactly("wonder", "treasury", "military", "civilian", "commercial", "scientific", "guilds");
        assertThat(module.scorePad().categoriesFor(BOTH, false))
                .extracting(ScoreCategory::key)
                .containsExactly(
                        "wonder", "treasury", "military", "civilian", "commercial", "scientific", "guilds", "cities", "leaders");
        assertThat(results(BOTH, both(pad(10, 4, 9, 15, 5, 13, 7), 6, 9), both(pad(7, 2, 5, 20, 8, 1, 4), 3, 2), both(pad(3, 6, 0, 12, 2, 25, 6), 0, 5)))
                .extracting(PlayerResult::total)
                .containsExactly(78, 52, 59);
        assertRefused("notRecorded", entry(BASE_GAME, both(pad(10, 4, 9, 15, 5, 13, 7), 6, 9), pad(7, 2, 5, 20, 8, 1, 4), pad(3, 6, 0, 12, 2, 25, 6)));
    }

    /** Each Player's Wonder board and its side; each board exists once. */
    @Test
    void eachPlayerHasAWonderOfTheirOwnOnOneOfItsSides() {
        PlayerEntry babylonAtNight = as(pad(10, 4, 9, 15, 5, 13, 7), Map.of("wonder", "babylon", "side", "night"));
        PlayerEntry gizaByDay = as(pad(7, 2, 5, 20, 8, 1, 4), Map.of("wonder", "giza", "side", "day"));
        PlayerEntry nobodyRemembers = pad(3, 6, 0, 12, 2, 25, 6);

        assertThat(results(BASE_GAME, babylonAtNight, gizaByDay, nobodyRemembers)).hasSize(3);
        assertRefused(
                "chosenTwice",
                entry(BASE_GAME, babylonAtNight, as(pad(7, 2, 5, 20, 8, 1, 4), Map.of("wonder", "babylon", "side", "day")), nobodyRemembers));
        assertRefused(
                "notRecorded",
                entry(BASE_GAME, as(pad(10, 4, 9, 15, 5, 13, 7), Map.of("wonder", "atlantis")), gizaByDay, nobodyRemembers));
    }

    @Test
    void offersTheWondersOfTheBaseGameTheExpansionsAndThePromo() {
        assertThat(module.scorePad().playerSetup().getFirst().options())
                .containsExactly(
                        "alexandria",
                        "babylon",
                        "ephesus",
                        "giza",
                        "halicarnassus",
                        "olympia",
                        "rhodes",
                        "roma",
                        "abuSimbel",
                        "byzantium",
                        "petra",
                        "mannekenPis");
    }

    /** The Wonders of an Expansion are only played with it; the promotional Manneken Pis always can be. */
    @Test
    void offersTheWondersOfAnExpansionOnlyWithIt() {
        PlayerSetupChoice wonder = module.scorePad().playerSetup().getFirst();
        PlayerEntry roma = as(pad(10, 4, 9, 15, 5, 13, 7), Map.of("wonder", "roma"));
        PlayerEntry petra = as(pad(7, 2, 5, 20, 8, 1, 4), Map.of("wonder", "petra"));
        PlayerEntry mannekenPis = as(pad(3, 6, 0, 12, 2, 25, 6), Map.of("wonder", "mannekenPis"));

        assertThat(wonder.optionsFor(module.scorePad(), BASE_GAME))
                .doesNotContain("roma", "abuSimbel", "byzantium", "petra")
                .contains("mannekenPis");
        assertThat(wonder.optionsFor(module.scorePad(), Map.of("leaders", "true")))
                .contains("roma", "abuSimbel")
                .doesNotContain("byzantium", "petra");
        assertRefused("needsExpansion", entry(BASE_GAME, roma, petra, mannekenPis));
        assertThat(results(BOTH, both(roma, 6, 9), both(petra, 3, 2), both(mannekenPis, 0, 5))).hasSize(3);
    }

    @Test
    void isForThreeToSevenPlayers() {
        PlayerEntry anyone = pad(1, 1, 1, 1, 1, 1, 1);

        assertRefused("tooFewPlayers", entry(BASE_GAME, anyone, anyone));
        assertThat(results(BASE_GAME, anyone, anyone, anyone, anyone, anyone, anyone, anyone)).hasSize(7);
        assertRefused("tooManyPlayers", entry(BASE_GAME, anyone, anyone, anyone, anyone, anyone, anyone, anyone, anyone));
    }

    /** Everything the pad names has a text in English, and Dutch and Hungarian have the same texts. */
    @Test
    void hasATextForEverythingOnItsPadInEveryLanguage() throws Exception {
        String prefix = SevenWondersGameModule.KEY + ".";
        Properties english = texts("en");
        assertThat(english).containsKeys(prefix + "name", prefix + "tie");
        for (Expansion expansion : module.scorePad().expansions()) {
            assertThat(english).containsKey(prefix + "expansion." + expansion.key());
        }
        for (PlayerSetupChoice choice : module.scorePad().playerSetup()) {
            assertThat(english).containsKey(prefix + "playerSetup." + choice.key());
            for (String option : choice.options()) {
                assertThat(english).containsKey(prefix + "playerSetup." + choice.key() + "." + option);
            }
        }
        for (ScoreCategory category : module.scorePad().categories()) {
            assertThat(english).containsKey(prefix + "category." + category.key());
        }
        assertThat(texts("nl").keySet()).isEqualTo(english.keySet());
        assertThat(texts("hu").keySet()).isEqualTo(english.keySet());
    }

    private Properties texts(String language) throws Exception {
        Properties texts = new Properties();
        try (var file = getClass().getResourceAsStream("/i18n/seven-wonders_" + language + ".properties")) {
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

    /** A Player's column of the base game's score pad, row by row. */
    private static PlayerEntry pad(
            int wonder, int treasury, int military, int civilian, int commercial, int scientific, int guilds) {
        Map<String, Integer> scores = new HashMap<>();
        scores.put("wonder", wonder);
        scores.put("treasury", treasury);
        scores.put("military", military);
        scores.put("civilian", civilian);
        scores.put("commercial", commercial);
        scores.put("scientific", scientific);
        scores.put("guilds", guilds);
        return new PlayerEntry(scores, false);
    }

    /** The column with both expansions: the black cards and the Leaders. */
    private static PlayerEntry both(PlayerEntry base, int cities, int leaders) {
        Map<String, Integer> scores = new HashMap<>(base.scores());
        scores.put("cities", cities);
        scores.put("leaders", leaders);
        return new PlayerEntry(scores, false, base.setup());
    }

    /** The same column, with this Player Setup. */
    private static PlayerEntry as(PlayerEntry player, Map<String, String> setup) {
        return new PlayerEntry(player.scores(), false, setup);
    }
}
