package io.github.oliverjuhasz.boardkeeper.gamemodules.calico;

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
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

class CalicoGameModuleTests {

    private static final Map<String, String> STANDARD = Map.of();
    private static final Map<String, String> FAMILY = Map.of("family", "true");

    private final CalicoGameModule module = new CalicoGameModule();

    @Test
    void isFoundThroughTheServiceLoader() {
        assertThat(ServiceLoader.load(GameModule.class)).extracting(GameModule::id).contains(CalicoGameModule.ID);
    }

    /** The scoring example on page 8 of the rulebook: Ari scores 28, 23 and 21, which makes 72. */
    @Test
    void addsUpDesignGoalsCatsAndButtonsAsInTheRulebooksExample() {
        assertThat(results(STANDARD, pad(28, 23, 21), pad(21, 19, 18)))
                .containsExactly(new PlayerResult(72, 1, true), new PlayerResult(58, 2, false));
    }

    @Test
    void theMostPointsWin() {
        assertThat(results(STANDARD, pad(10, 10, 9), pad(7, 25, 12), pad(21, 3, 15), pad(0, 0, 0)))
                .extracting(PlayerResult::placing)
                .containsExactly(3, 1, 2, 4);
    }

    /** Cat tokens decide a tie, and the pad does not say how many each Player has. */
    @Test
    void aTieForFirstPlaceIsLeftToTheRecorder() {
        Outcome.Competitive tied = score(STANDARD, pad(28, 23, 21), pad(30, 21, 21), pad(10, 10, 9));

        assertThat(tied.results()).extracting(PlayerResult::placing).containsExactly(1, 1, 3);
        assertThat(tied.tieForRecorder()).containsExactly(0, 1);

        Outcome.Competitive decided = score(STANDARD, pad(28, 23, 21), winnerOfTheTie(pad(30, 21, 21)), pad(10, 10, 9));
        assertThat(decided.results())
                .containsExactly(new PlayerResult(72, 2, false), new PlayerResult(72, 1, true), new PlayerResult(29, 3, false));
    }

    @Test
    void playersLevelFurtherDownShareAPlacing() {
        Outcome.Competitive outcome = score(STANDARD, pad(30, 30, 30), pad(10, 10, 9), pad(9, 11, 9));

        assertThat(outcome.results()).extracting(PlayerResult::placing).containsExactly(1, 2, 2);
        assertThat(outcome.tieForRecorder()).isEmpty();
    }

    @Test
    void theFamilyVariantScoresNoDesignGoals() {
        assertThat(results(FAMILY, family(23, 21), family(25, 24)))
                .containsExactly(new PlayerResult(44, 2, false), new PlayerResult(49, 1, true));
        // A design goals row is not part of the family variant's pad...
        assertRefused("notRecorded", entry(FAMILY, pad(28, 23, 21), family(25, 24)));
        // ...and has to be filled in without it.
        assertRefused("scoreMissing", entry(STANDARD, family(23, 21), pad(28, 23, 21)));
    }

    @Test
    void buttonsAreWorthThreePointsEach() {
        assertRefused("scoreNotAllowed", entry(STANDARD, pad(28, 23, 20), pad(21, 19, 18)));
        assertRefused("scoreNotAllowed", entry(STANDARD, pad(28, -3, 21), pad(21, 19, 18)));
    }

    @Test
    void isRecordedForTwoToFourPlayers() {
        assertRefused("tooFewPlayers", entry(STANDARD, pad(28, 23, 21)));
        assertRefused(
                "tooManyPlayers",
                entry(STANDARD, pad(1, 1, 3), pad(1, 1, 3), pad(1, 1, 3), pad(1, 1, 3), pad(1, 1, 3)));
    }

    @Test
    void canBeRecordedWithOnlyTheWinner() {
        PlayEntry entry = new PlayEntry(
                RecordingMode.WINNER_ONLY,
                STANDARD,
                List.of(new PlayerEntry(Map.of(), true), new PlayerEntry(Map.of(), false)),
                null);

        assertThat(((Outcome.Competitive) module.score(entry)).results())
                .containsExactly(new PlayerResult(null, null, true), new PlayerResult(null, null, false));
    }

    /** Everything the sheet names has a text in English, and Dutch and Hungarian have the same texts. */
    @Test
    void hasATextForEverythingOnItsSheetInEveryLanguage() throws Exception {
        Properties english = texts("en");
        assertThat(english).containsKeys("calico.name", "calico.tie");
        for (Variant variant : module.scoreSheet().variants()) {
            assertThat(english).containsKey("calico.variant." + variant.key());
        }
        for (ScoreCategory category : module.scoreSheet().categories()) {
            assertThat(english).containsKey("calico.category." + category.key());
        }
        assertThat(texts("nl").keySet()).isEqualTo(english.keySet());
        assertThat(texts("hu").keySet()).isEqualTo(english.keySet());
    }

    private Properties texts(String language) throws Exception {
        Properties texts = new Properties();
        try (var file = getClass().getResourceAsStream("/i18n/calico_" + language + ".properties")) {
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

    private static PlayerEntry pad(int designGoals, int cats, int buttons) {
        return new PlayerEntry(Map.of("designGoals", designGoals, "cats", cats, "buttons", buttons), false);
    }

    /** A Player's pad in the family variant, which has no design goals row. */
    private static PlayerEntry family(int cats, int buttons) {
        return new PlayerEntry(Map.of("cats", cats, "buttons", buttons), false);
    }

    private static PlayerEntry winnerOfTheTie(PlayerEntry player) {
        return new PlayerEntry(player.scores(), true);
    }
}
