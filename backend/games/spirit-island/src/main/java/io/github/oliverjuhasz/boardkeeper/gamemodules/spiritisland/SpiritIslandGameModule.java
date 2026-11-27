package io.github.oliverjuhasz.boardkeeper.gamemodules.spiritisland;

import io.github.oliverjuhasz.boardkeeper.gameapi.CooperativeGameModule;
import io.github.oliverjuhasz.boardkeeper.gameapi.PlayerSetupChoice;
import io.github.oliverjuhasz.boardkeeper.gameapi.RecordingMode;
import io.github.oliverjuhasz.boardkeeper.gameapi.ScoreCategory;
import io.github.oliverjuhasz.boardkeeper.gameapi.ScorePad;
import io.github.oliverjuhasz.boardkeeper.gameapi.Variant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Game Module for Spirit Island (Greater Than Games, 2017), without its expansions.
 *
 * <p>The rules are those of the publisher's rulebook: "Scoring" on page 23 and the "Adversary &amp;
 * Scenario Difficulty Chart" on page 28.
 *
 * <ul>
 *   <li>Spirit Island is a cooperative game for one to four players. Each player is a Spirit,
 *       and each Spirit is played by one player at most.
 *   <li>A game can be played against an Adversary, at its base level or at a level from 1 to 6,
 *       and with a Scenario and the thematic map. Together they make the game's Difficulty: the
 *       adversary's at its level, plus the scenario's, plus 3 for the thematic map ("Add the
 *       Difficulty of a Scenario and Adversary together").
 *   <li>Scoring is optional ("If you would like to score your games"), so a Play starts with only
 *       whether the Spirits won. With a score, after a victory: "Score 5x Difficulty, +10 bonus for
 *       winning, +2 per Invader Card remaining in the deck". After a defeat: "Score 2x
 *       Difficulty, +1 per Invader Card not in the deck". Either way: "+1 per X living Dahan and
 *       -1 per X Blight on the Island, where X is the number of players in the game". The rulebook
 *       doesn't say how to round, so Board Keeper counts only complete groups of X.
 *   <li>The Invader cards are entered as the rule counts them: those left in the deck after a
 *       victory, and those no longer in it after a defeat.
 * </ul>
 */
public final class SpiritIslandGameModule extends CooperativeGameModule {

    /** Stored with every Play recorded through this module. Never change it (ADR 0012). */
    public static final UUID ID = UUID.fromString("d17ecfcd-9564-4576-a5c8-589e82c10cc4");

    public static final String KEY = "spirit-island";

    /** The Variant "which Adversary": {@code none} or one of the three. */
    public static final String ADVERSARY = "adversary";

    /** The Variant "which Scenario". */
    public static final String SCENARIO = "scenario";

    /** The on/off Variant "played on the thematic map". */
    public static final String THEMATIC_MAP = "thematicMap";

    /**
     * Each Adversary's Difficulty at its base level (0) and at levels 1 to 6, from the chart on
     * page 28. Its level is a Variant of its own, named after it, such as {@code englandLevel}.
     */
    static final Map<String, List<Integer>> ADVERSARY_DIFFICULTY = Map.of(
            "brandenburgPrussia", List.of(1, 2, 4, 6, 7, 9, 10),
            "england", List.of(1, 3, 4, 6, 7, 9, 10),
            "sweden", List.of(1, 2, 3, 5, 6, 7, 8));

    /** Each Scenario's Difficulty, from the same chart. */
    static final Map<String, Integer> SCENARIO_DIFFICULTY = Map.of(
            "none", 0, "blitz", 0, "guardTheIslesHeart", 0, "ritualsOfTerror", 3, "dahanInsurrection", 4);

    /** "Using thematic maps increases the game difficulty by 3." */
    static final int THEMATIC_MAP_DIFFICULTY = 3;

    private static final List<String> ADVERSARIES = List.of("brandenburgPrussia", "england", "sweden");

    private static final ScorePad PAD = ScorePad.cooperative(
            1,
            4,
            // No Expansions.
            List.of(),
            List.of(
                    new Variant.Choice(ADVERSARY, List.of("none", "brandenburgPrussia", "england", "sweden")),
                    levelOf("brandenburgPrussia"),
                    levelOf("england"),
                    levelOf("sweden"),
                    new Variant.Choice(
                            SCENARIO, List.of("none", "blitz", "guardTheIslesHeart", "ritualsOfTerror", "dahanInsurrection")),
                    new Variant.OnOff(THEMATIC_MAP, false)),
            // The group's rows: what the score is worked out from.
            List.of(ScoreCategory.of("invaderCards"), ScoreCategory.of("dahan"), ScoreCategory.of("blight")),
            List.of(PlayerSetupChoice.of(
                            "spirit",
                            List.of(
                                    "lightningsSwiftStrike",
                                    "riverSurgesInSunlight",
                                    "shadowsFlickerLikeFlame",
                                    "vitalStrengthOfTheEarth",
                                    "aSpreadOfRampantGreen",
                                    "thunderspeaker",
                                    "bringerOfDreamsAndNightmares",
                                    "oceansHungryGrasp"))
                    .eachOptionOncePerPlay()),
            // Scoring is optional: a Play starts with only whether the Spirits won.
            RecordingMode.WINNER_ONLY);

    /** An Adversary's level, chosen once that Adversary is: its base level (0), or 1 to 6. */
    private static Variant levelOf(String adversary) {
        return new Variant.Range(adversary + "Level", 0, 6).onlyWhen(ADVERSARY, adversary);
    }

    @Override
    public UUID id() {
        return ID;
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public ScorePad scorePad() {
        return PAD;
    }

    @Override
    protected int groupScore(Map<String, String> setup, boolean won, Map<String, Integer> rows, int players) {
        int difficulty = difficulty(setup);
        int invaderCards = rows.get("invaderCards");
        // Integer division rounds down: only complete groups of X count.
        int dahanAndBlight = rows.get("dahan") / players - rows.get("blight") / players;
        return won
                ? 5 * difficulty + 10 + 2 * invaderCards + dahanAndBlight
                : 2 * difficulty + invaderCards + dahanAndBlight;
    }

    /** The game's Difficulty: the Adversary's at its level, the Scenario's, and the thematic map's. */
    static int difficulty(Map<String, String> setup) {
        String adversary = PAD.valueOf(ADVERSARY, setup);
        int fromAdversary = ADVERSARIES.contains(adversary)
                ? ADVERSARY_DIFFICULTY.get(adversary).get(Integer.parseInt(PAD.valueOf(adversary + "Level", setup)))
                : 0;
        int fromScenario = SCENARIO_DIFFICULTY.get(PAD.valueOf(SCENARIO, setup));
        int fromMap = Boolean.parseBoolean(PAD.valueOf(THEMATIC_MAP, setup)) ? THEMATIC_MAP_DIFFICULTY : 0;
        return fromAdversary + fromScenario + fromMap;
    }
}
