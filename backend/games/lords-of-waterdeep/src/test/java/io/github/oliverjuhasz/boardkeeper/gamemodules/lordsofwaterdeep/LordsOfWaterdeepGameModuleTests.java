package io.github.oliverjuhasz.boardkeeper.gamemodules.lordsofwaterdeep;

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

class LordsOfWaterdeepGameModuleTests {

    private static final Map<String, String> BASE_GAME = Map.of();
    private static final Map<String, String> UNDERMOUNTAIN = Map.of("undermountain", "true");
    private static final Map<String, String> SKULLPORT = Map.of("skullport", "true");

    private final LordsOfWaterdeepGameModule module = new LordsOfWaterdeepGameModule();

    @Test
    void isFoundThroughTheServiceLoader() {
        assertThat(ServiceLoader.load(GameModule.class)).extracting(GameModule::id).contains(LordsOfWaterdeepGameModule.ID);
    }

    /**
     * Page 11, "Final Scoring": the Victory Points on the scoring track, then 1 per Adventurer in the
     * Tavern, 1 per 2 Gold and the Lord card. The most VP wins.
     */
    @Test
    void addsUpTheScoringTrackAndTheFinalScoring() {
        assertThat(results(BASE_GAME, pad(98, 6, 3, 24), pad(110, 2, 1, 16), pad(87, 9, 4, 28)))
                .containsExactly(
                        new PlayerResult(131, 1, true), new PlayerResult(129, 2, false), new PlayerResult(128, 3, false));
    }

    /**
     * Page 11: "In the event of a tie, the player with the most Gold wins." More points for Gold
     * always means more Gold; equal points for Gold, as for 4 and 5 Gold, are left to the recorder.
     */
    @Test
    void aTieGoesToTheMostGoldAsFarAsItsPointsTell() {
        Outcome.Competitive byGold = score(BASE_GAME, pad(100, 4, 3, 12), pad(100, 5, 2, 12), pad(90, 0, 0, 0));
        assertThat(byGold.results()).extracting(PlayerResult::placing).containsExactly(1, 2, 3);
        assertThat(byGold.tieForRecorder()).isEmpty();

        Outcome.Competitive leftToTheRecorder = score(BASE_GAME, pad(100, 4, 2, 12), pad(100, 4, 2, 12), pad(90, 0, 0, 0));
        assertThat(leftToTheRecorder.results()).extracting(PlayerResult::placing).containsExactly(1, 1, 3);
        assertThat(leftToTheRecorder.tieForRecorder()).containsExactly(0, 1);
        assertThat(module.scorePad().recorderDecidesTies()).isTrue();
    }

    /** Page 4: two to five players. Skullport, page 5: six with at least one of its halves. */
    @Test
    void isForTwoToFivePlayersAndSixWithEitherHalfOfTheExpansion() {
        PlayerEntry anyone = pad(1, 1, 1, 1);
        PlayerEntry[] six = {anyone, anyone, anyone, anyone, anyone, anyone};

        assertRefused("tooFewPlayers", entry(BASE_GAME, anyone));
        assertThat(results(BASE_GAME, anyone, anyone, anyone, anyone, anyone)).hasSize(5);
        assertRefused("tooManyPlayers", entry(BASE_GAME, six));
        assertThat(results(UNDERMOUNTAIN, six)).hasSize(6);
        PlayerEntry corrupt = corrupt(anyone, 0);
        assertThat(results(SKULLPORT, corrupt, corrupt, corrupt, corrupt, corrupt, corrupt)).hasSize(6);
        assertRefused("tooManyPlayers", entry(UNDERMOUNTAIN, anyone, anyone, anyone, anyone, anyone, anyone, anyone));
    }

    /**
     * Skullport, page 8: each Corruption token costs the value of the farthest empty space of the
     * Corruption track; in its example, -5. The row holds the points lost.
     */
    @Test
    void skullportLosesPointsForCorruption() {
        assertThat(module.scorePad().categoriesFor(SKULLPORT, false))
                .extracting(ScoreCategory::key)
                .containsExactly("scoringTrack", "adventurers", "gold", "lordCard", "corruption");
        assertThat(module.scorePad().categoriesFor(UNDERMOUNTAIN, false))
                .extracting(ScoreCategory::key)
                .doesNotContain("corruption");
        // Three tokens at -5 each.
        assertThat(results(SKULLPORT, corrupt(pad(98, 6, 3, 24), -15), corrupt(pad(110, 2, 1, 16), 0)))
                .extracting(PlayerResult::total)
                .containsExactly(116, 129);
        assertRefused("scoreNotAllowed", entry(SKULLPORT, corrupt(pad(98, 6, 3, 24), 5), corrupt(pad(110, 2, 1, 16), 0)));
        assertRefused("notRecorded", entry(BASE_GAME, corrupt(pad(98, 6, 3, 24), -15), pad(110, 2, 1, 16)));
    }

    /** Each player's Lord of Waterdeep, dealt once; the expansion's Lords come with their half. */
    @Test
    void eachPlayerIsALordOfWaterdeepAndTheExpansionsLordsNeedTheirHalf() {
        PlayerSetupChoice lord = module.scorePad().playerSetup().getFirst();

        assertThat(lord.optionsFor(module.scorePad(), BASE_GAME)).hasSize(11).doesNotContain("theXanathar");
        assertThat(lord.optionsFor(module.scorePad(), UNDERMOUNTAIN))
                .hasSize(14)
                .contains("daniloThann", "halasterBlackcloak", "trobriand")
                .doesNotContain("theXanathar");
        assertThat(lord.optionsFor(module.scorePad(), SKULLPORT))
                .hasSize(14)
                .contains("irusylEraneth", "sangalor", "theXanathar");
        assertThat(results(BASE_GAME, as(pad(98, 6, 3, 24), "mirtTheMoneylender"), as(pad(110, 2, 1, 16), "larissaNeathal")))
                .hasSize(2);
        assertRefused(
                "chosenTwice",
                entry(BASE_GAME, as(pad(98, 6, 3, 24), "mirtTheMoneylender"), as(pad(110, 2, 1, 16), "mirtTheMoneylender")));
        assertRefused(
                "needsExpansion",
                entry(UNDERMOUNTAIN, as(pad(98, 6, 3, 24), "theXanathar"), as(pad(110, 2, 1, 16), "trobriand")));
        assertThat(results(SKULLPORT, as(corrupt(pad(98, 6, 3, 24), -4), "theXanathar"), corrupt(pad(110, 2, 1, 16), 0)))
                .hasSize(2);
    }

    /** Everything the pad names has a text in English, and Dutch and Hungarian have the same texts. */
    @Test
    void hasATextForEverythingOnItsPadInEveryLanguage() throws Exception {
        String prefix = LordsOfWaterdeepGameModule.KEY + ".";
        Properties english = texts("en");
        assertThat(english).containsKeys(prefix + "name", prefix + "tie");
        for (Expansion expansion : module.scorePad().expansions()) {
            assertThat(english).containsKey(prefix + "expansion." + expansion.key());
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
        try (var file = getClass().getResourceAsStream("/i18n/lords-of-waterdeep_" + language + ".properties")) {
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

    /** A Player's rows of the base game, in points. */
    private static PlayerEntry pad(int scoringTrack, int adventurers, int gold, int lordCard) {
        return new PlayerEntry(
                Map.of("scoringTrack", scoringTrack, "adventurers", adventurers, "gold", gold, "lordCard", lordCard),
                false);
    }

    /** The same rows with Skullport's row for Corruption, in the points lost. */
    private static PlayerEntry corrupt(PlayerEntry player, int corruption) {
        Map<String, Integer> scores = new HashMap<>(player.scores());
        scores.put("corruption", corruption);
        return new PlayerEntry(scores, false, player.setup());
    }

    /** The same rows, as this Lord of Waterdeep. */
    private static PlayerEntry as(PlayerEntry player, String lord) {
        return new PlayerEntry(player.scores(), false, Map.of("lord", lord));
    }
}
