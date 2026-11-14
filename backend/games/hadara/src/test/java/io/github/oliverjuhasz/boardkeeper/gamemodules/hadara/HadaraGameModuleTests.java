package io.github.oliverjuhasz.boardkeeper.gamemodules.hadara;

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

class HadaraGameModuleTests {

    private static final Map<String, String> NO_SETUP = Map.of();
    private static final Map<String, String> GOD_MODE = Map.of("npcLevel", "godMode");

    private final HadaraGameModule module = new HadaraGameModule();

    @Test
    void isFoundThroughTheServiceLoader() {
        assertThat(ServiceLoader.load(GameModule.class)).extracting(GameModule::id).contains(HadaraGameModule.ID);
    }

    /** Page 10: the six rows add up, and the most points wins. */
    @Test
    void addsUpTheSixRowsOfTheScorePad() {
        assertThat(results(NO_SETUP, pad(26, 12, 23, 21, 47, 2), pad(30, 15, 14, 14, 40, 3), pad(18, 9, 10, 0, 35, 1)))
                .containsExactly(
                        new PlayerResult(131, 1, true), new PlayerResult(116, 2, false), new PlayerResult(73, 3, false));
    }

    /** A gold medal scores 7 points for each set of five differently-colored cards. */
    @Test
    void goldMedalsScoreInSevens() {
        assertThat(results(NO_SETUP, pad(26, 12, 23, 42, 47, 2), pad(30, 15, 14, 0, 40, 3))).hasSize(2);
        assertRefused("scoreNotAllowed", entry(NO_SETUP, pad(26, 12, 23, 20, 47, 2), pad(30, 15, 14, 14, 40, 3)));
    }

    @Test
    void noRowScoresBelowZero() {
        assertRefused("scoreNotAllowed", entry(NO_SETUP, pad(26, 12, 23, 21, 47, -1), pad(30, 15, 14, 14, 40, 3)));
    }

    /** The leftover coins that decide a tie are not on the pad. */
    @Test
    void aTieForFirstPlaceIsLeftToTheRecorder() {
        Outcome.Competitive tied = score(NO_SETUP, pad(26, 12, 23, 21, 47, 2), pad(30, 15, 23, 14, 46, 3));

        assertThat(tied.results()).extracting(PlayerResult::placing).containsExactly(1, 1);
        assertThat(tied.tieForRecorder()).containsExactly(0, 1);
    }

    /** Solo rules, page 2: "Add up your points and the NPC's points to determine the winner!" */
    @Test
    void aSoloPlayIsWonByBeatingTheNpcsScore() {
        assertThat(results(NO_SETUP, solo(pad(26, 12, 23, 21, 47, 2), 120)))
                .containsExactly(new PlayerResult(131, null, true));
        assertThat(results(GOD_MODE, solo(pad(26, 12, 23, 21, 47, 2), 140)))
                .containsExactly(new PlayerResult(131, null, false));
    }

    /** The NPC never has coins, so it can't win a tie on leftover coins. */
    @Test
    void aSoloPlayTiedWithTheNpcIsWon() {
        assertThat(results(NO_SETUP, solo(pad(26, 12, 23, 21, 47, 2), 131)))
                .containsExactly(new PlayerResult(131, null, true));
    }

    @Test
    void theNpcBelongsToSoloPlaysOnly() {
        // Its score has to be entered alone...
        assertRefused("scoreMissing", entry(NO_SETUP, pad(26, 12, 23, 21, 47, 2)));
        // ...and only then.
        PlayerEntry withNpc = solo(pad(26, 12, 23, 21, 47, 2), 120);
        assertRefused("notRecorded", entry(NO_SETUP, withNpc, pad(30, 15, 14, 14, 40, 3)));
        // The same goes for its level, which is 1 to 11 or God mode.
        assertRefused("notRecorded", entry(GOD_MODE, pad(26, 12, 23, 21, 47, 2), pad(30, 15, 14, 14, 40, 3)));
        assertThat(results(Map.of("npcLevel", "11"), withNpc)).hasSize(1);
        assertRefused("notRecorded", entry(Map.of("npcLevel", "12"), withNpc));
    }

    @Test
    void isRecordedForOneToFivePlayers() {
        PlayerEntry anyone = pad(1, 1, 1, 0, 1, 1);

        assertRefused("tooFewPlayers", entry(NO_SETUP));
        assertThat(results(NO_SETUP, anyone, anyone, anyone, anyone, anyone)).hasSize(5);
        assertRefused("tooManyPlayers", entry(NO_SETUP, anyone, anyone, anyone, anyone, anyone, anyone));
    }

    /** Everything the pad names has a text in English, and Dutch and Hungarian have the same texts. */
    @Test
    void hasATextForEverythingOnItsPadInEveryLanguage() throws Exception {
        Properties english = texts("en");
        assertThat(english).containsKeys("hadara.name", "hadara.tie");
        for (Variant variant : module.scorePad().variants()) {
            assertThat(english).containsKey("hadara.variant." + variant.key());
            if (variant instanceof Variant.Choice choice) {
                for (String option : choice.options()) {
                    assertThat(english).containsKey("hadara.variant." + variant.key() + "." + option);
                }
            }
        }
        for (ScoreCategory category : module.scorePad().categories()) {
            assertThat(english).containsKey("hadara.category." + category.key());
        }
        assertThat(texts("nl").keySet()).isEqualTo(english.keySet());
        assertThat(texts("hu").keySet()).isEqualTo(english.keySet());
    }

    private Properties texts(String language) throws Exception {
        Properties texts = new Properties();
        try (var file = getClass().getResourceAsStream("/i18n/hadara_" + language + ".properties")) {
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
            int colonies, int statues, int silverMedals, int goldMedals, int cards, int remainingCoins) {
        Map<String, Integer> scores = new HashMap<>();
        scores.put("colonies", colonies);
        scores.put("statues", statues);
        scores.put("silverMedals", silverMedals);
        scores.put("goldMedals", goldMedals);
        scores.put("cards", cards);
        scores.put("remainingCoins", remainingCoins);
        return new PlayerEntry(scores, false);
    }

    /** The pad of a Solo Play, with the NPC's score. */
    private static PlayerEntry solo(PlayerEntry player, int npc) {
        Map<String, Integer> scores = new HashMap<>(player.scores());
        scores.put("npc", npc);
        return new PlayerEntry(scores, false);
    }
}
