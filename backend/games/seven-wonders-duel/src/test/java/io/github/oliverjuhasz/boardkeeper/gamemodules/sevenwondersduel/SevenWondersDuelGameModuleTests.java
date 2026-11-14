package io.github.oliverjuhasz.boardkeeper.gamemodules.sevenwondersduel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.github.oliverjuhasz.boardkeeper.gameapi.Expansion;
import io.github.oliverjuhasz.boardkeeper.gameapi.GameModule;
import io.github.oliverjuhasz.boardkeeper.gameapi.InvalidPlayException;
import io.github.oliverjuhasz.boardkeeper.gameapi.Outcome;
import io.github.oliverjuhasz.boardkeeper.gameapi.PlayEntry;
import io.github.oliverjuhasz.boardkeeper.gameapi.PlayerEntry;
import io.github.oliverjuhasz.boardkeeper.gameapi.PlayerResult;
import io.github.oliverjuhasz.boardkeeper.gameapi.RecordingMode;
import io.github.oliverjuhasz.boardkeeper.gameapi.ScoreCategory;
import io.github.oliverjuhasz.boardkeeper.gameapi.VictoryType;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

class SevenWondersDuelGameModuleTests {

    private static final Map<String, String> BASE_GAME = Map.of();
    private static final Map<String, String> PANTHEON = Map.of("pantheon", "true");
    private static final Map<String, String> AGORA = Map.of("agora", "true");
    private static final Map<String, String> BOTH = Map.of("pantheon", "true", "agora", "true");

    private final SevenWondersDuelGameModule module = new SevenWondersDuelGameModule();

    @Test
    void isFoundThroughTheServiceLoader() {
        assertThat(ServiceLoader.load(GameModule.class))
                .extracting(GameModule::id)
                .contains(SevenWondersDuelGameModule.ID);
    }

    /** Page 13: a civilian victory goes to the most victory points. */
    @Test
    void aCivilianVictoryAddsUpTheScorePad() {
        assertThat(results(onPoints(BASE_GAME, base(20, 8, 5, 6, 15, 4, 3, 2), base(12, 10, 4, 3, 13, 0, 5, 5))))
                .containsExactly(new PlayerResult(63, 1, true), new PlayerResult(52, 2, false));
    }

    /** Page 13: the most points from Civilian Buildings break a tie, and then it is shared. */
    @Test
    void aTieGoesToTheMostBlueCardPointsAndIsThenShared() {
        Outcome.Competitive blue = score(onPoints(BASE_GAME, base(20, 8, 5, 6, 15, 4, 3, 2), base(23, 8, 5, 3, 15, 4, 3, 2)));
        assertThat(blue.results()).extracting(PlayerResult::placing).containsExactly(2, 1);
        assertThat(blue.tieForRecorder()).isEmpty();

        Outcome.Competitive shared = score(onPoints(BASE_GAME, base(20, 8, 5, 6, 15, 4, 3, 2), base(20, 10, 3, 6, 15, 4, 3, 2)));
        assertThat(shared.results())
                .containsExactly(new PlayerResult(63, 1, true), new PlayerResult(63, 1, true));
        assertThat(shared.tieForRecorder()).isEmpty();
    }

    /** Pages 12 and 13: a supremacy ends the game at once, so only its winner is recorded. */
    @Test
    void aSupremacyHasItsWinnerAndNoScores() {
        assertThat(results(outright("militarySupremacy", BASE_GAME, true, false)))
                .containsExactly(new PlayerResult(null, null, true), new PlayerResult(null, null, false));
        assertThat(results(outright("scientificSupremacy", BASE_GAME, false, true)))
                .extracting(PlayerResult::won)
                .containsExactly(false, true);
        assertRefused("oneWinnerOnly", outright("militarySupremacy", BASE_GAME, true, true));
        assertRefused(
                "notRecorded",
                new PlayEntry(
                        RecordingMode.POINTS,
                        BASE_GAME,
                        List.of(base(20, 8, 5, 6, 15, 4, 3, 2), base(12, 10, 4, 3, 13, 0, 5, 5)),
                        null,
                        "militarySupremacy"));
    }

    @Test
    void aPlaySaysHowItWasWon() {
        assertRefused(
                "victoryTypeMissing",
                new PlayEntry(
                        RecordingMode.POINTS,
                        BASE_GAME,
                        List.of(base(20, 8, 5, 6, 15, 4, 3, 2), base(12, 10, 4, 3, 13, 0, 5, 5)),
                        null));
    }

    /** Pantheon, page 7: Grand Temples take the place of the Guilds, and Divinities score too. */
    @Test
    void pantheonSwapsTheGuildsForGrandTemplesAndAddsTheDivinities() {
        assertThat(module.scorePad().categoriesFor(PANTHEON, false))
                .extracting(ScoreCategory::key)
                .containsExactly(
                        "civilian",
                        "scientific",
                        "commercial",
                        "grandTemples",
                        "divinities",
                        "wonders",
                        "progressTokens",
                        "coins",
                        "military");
        PlayerEntry withTemples = pantheon(base(20, 8, 5, 0, 15, 4, 3, 2), 12, 9);
        assertThat(results(onPoints(PANTHEON, withTemples, pantheon(base(12, 10, 4, 0, 13, 0, 5, 5), 5, 0))))
                .extracting(PlayerResult::total)
                .containsExactly(78, 54);
        // Three Grand Temples are worth 21 points, the most there is.
        assertRefused("scoreNotAllowed", onPoints(PANTHEON, pantheon(base(20, 8, 5, 0, 15, 4, 3, 2), 22, 0), withTemples));
        // There are no Guilds.
        assertRefused("notRecorded", onPoints(PANTHEON, withGuilds(withTemples, 3), withTemples));
    }

    /** Agora, page 13: the Senate adds a row, and Political Supremacy a fourth way to win. */
    @Test
    void agoraAddsTheSenateAndPoliticalSupremacy() {
        assertThat(module.scorePad().categoriesFor(AGORA, false))
                .extracting(ScoreCategory::key)
                .containsExactly(
                        "civilian",
                        "scientific",
                        "commercial",
                        "guilds",
                        "wonders",
                        "progressTokens",
                        "coins",
                        "military",
                        "senate");
        assertThat(results(onPoints(AGORA, senate(base(20, 8, 5, 6, 15, 4, 3, 2), 4), senate(base(12, 10, 4, 3, 13, 0, 5, 5), 6))))
                .extracting(PlayerResult::total)
                .containsExactly(67, 58);
        assertThat(module.scorePad().victoryTypesFor(BASE_GAME))
                .extracting(VictoryType::key)
                .containsExactly("civilian", "militarySupremacy", "scientificSupremacy");
        assertThat(results(outright("politicalSupremacy", AGORA, true, false))).hasSize(2);
        assertRefused("notRecorded", outright("politicalSupremacy", BASE_GAME, true, false));
    }

    @Test
    void bothExpansionsCanBePlayedTogether() {
        PlayerEntry anna = senate(pantheon(base(20, 8, 5, 0, 15, 4, 3, 2), 12, 9), 4);
        PlayerEntry bence = senate(pantheon(base(12, 10, 4, 0, 13, 0, 5, 5), 5, 0), 6);

        assertThat(results(onPoints(BOTH, anna, bence))).extracting(PlayerResult::total).containsExactly(82, 60);
        assertThat(results(outright("politicalSupremacy", BOTH, false, true))).hasSize(2);
    }

    /** The military track gives 0, 2, 5 or 10 points. */
    @Test
    void theMilitaryTrackGivesTenPointsAtMost() {
        assertRefused(
                "scoreNotAllowed",
                onPoints(BASE_GAME, base(20, 8, 5, 6, 15, 4, 3, 11), base(12, 10, 4, 3, 13, 0, 5, 5)));
    }

    @Test
    void isForTwoPlayers() {
        PlayerEntry anyone = base(1, 1, 1, 1, 1, 1, 1, 1);

        assertRefused("tooFewPlayers", onPoints(BASE_GAME, anyone));
        assertRefused("tooManyPlayers", onPoints(BASE_GAME, anyone, anyone, anyone));
    }

    /** Everything the pad names has a text in English, and Dutch and Hungarian have the same texts. */
    @Test
    void hasATextForEverythingOnItsPadInEveryLanguage() throws Exception {
        String prefix = SevenWondersDuelGameModule.KEY + ".";
        Properties english = texts("en");
        assertThat(english).containsKey(prefix + "name");
        for (Expansion expansion : module.scorePad().expansions()) {
            assertThat(english).containsKey(prefix + "expansion." + expansion.key());
        }
        for (ScoreCategory category : module.scorePad().categories()) {
            assertThat(english).containsKey(prefix + "category." + category.key());
        }
        for (VictoryType victoryType : module.scorePad().victoryTypes()) {
            assertThat(english).containsKey(prefix + "victoryType." + victoryType.key());
        }
        assertThat(texts("nl").keySet()).isEqualTo(english.keySet());
        assertThat(texts("hu").keySet()).isEqualTo(english.keySet());
    }

    private Properties texts(String language) throws Exception {
        Properties texts = new Properties();
        try (var file = getClass().getResourceAsStream("/i18n/seven-wonders-duel_" + language + ".properties")) {
            texts.load(new InputStreamReader(file, StandardCharsets.UTF_8));
        }
        return texts;
    }

    private Outcome.Competitive score(PlayEntry entry) {
        return (Outcome.Competitive) module.score(entry);
    }

    private List<PlayerResult> results(PlayEntry entry) {
        return score(entry).results();
    }

    private void assertRefused(String reason, PlayEntry entry) {
        assertThatExceptionOfType(InvalidPlayException.class)
                .isThrownBy(() -> module.score(entry))
                .extracting(InvalidPlayException::reason)
                .isEqualTo(reason);
    }

    /** A Play won on points, a civilian victory. */
    private static PlayEntry onPoints(Map<String, String> setup, PlayerEntry... players) {
        return new PlayEntry(RecordingMode.POINTS, setup, Arrays.asList(players), null, "civilian");
    }

    /** A Play two Players ended outright, with whether each of them won. */
    private static PlayEntry outright(String victoryType, Map<String, String> setup, boolean first, boolean second) {
        return new PlayEntry(
                RecordingMode.WINNER_ONLY,
                setup,
                List.of(new PlayerEntry(Map.of(), first), new PlayerEntry(Map.of(), second)),
                null,
                victoryType);
    }

    /**
     * A Player's column of the base game's score pad, row by row. Without Pantheon, the fourth row
     * is the Guilds; with it, leave it 0 and use {@link #pantheon}.
     */
    private static PlayerEntry base(
            int civilian,
            int scientific,
            int commercial,
            int guilds,
            int wonders,
            int progressTokens,
            int coins,
            int military) {
        Map<String, Integer> scores = new HashMap<>();
        scores.put("civilian", civilian);
        scores.put("scientific", scientific);
        scores.put("commercial", commercial);
        scores.put("guilds", guilds);
        scores.put("wonders", wonders);
        scores.put("progressTokens", progressTokens);
        scores.put("coins", coins);
        scores.put("military", military);
        return new PlayerEntry(scores, false);
    }

    /** The column with Pantheon: no Guilds, but Grand Temples and Divinities. */
    private static PlayerEntry pantheon(PlayerEntry base, int grandTemples, int divinities) {
        Map<String, Integer> scores = new HashMap<>(base.scores());
        scores.remove("guilds");
        scores.put("grandTemples", grandTemples);
        scores.put("divinities", divinities);
        return new PlayerEntry(scores, false);
    }

    /** The column with Agora's Senate. */
    private static PlayerEntry senate(PlayerEntry player, int senate) {
        Map<String, Integer> scores = new HashMap<>(player.scores());
        scores.put("senate", senate);
        return new PlayerEntry(scores, false);
    }

    private static PlayerEntry withGuilds(PlayerEntry player, int guilds) {
        Map<String, Integer> scores = new HashMap<>(player.scores());
        scores.put("guilds", guilds);
        return new PlayerEntry(scores, false);
    }
}
