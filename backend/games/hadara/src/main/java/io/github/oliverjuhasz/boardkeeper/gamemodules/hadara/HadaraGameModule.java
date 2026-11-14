package io.github.oliverjuhasz.boardkeeper.gamemodules.hadara;

import io.github.oliverjuhasz.boardkeeper.gameapi.ScoreCategory;
import io.github.oliverjuhasz.boardkeeper.gameapi.ScorePad;
import io.github.oliverjuhasz.boardkeeper.gameapi.Solo;
import io.github.oliverjuhasz.boardkeeper.gameapi.StandardGameModule;
import io.github.oliverjuhasz.boardkeeper.gameapi.Variant;
import java.util.List;
import java.util.UUID;

/**
 * The Game Module for Hadara (Hans im Glück, 2019; Z-Man Games in English).
 *
 * <p>The rules are those of the publisher's rulebook, "Game End and Final Scoring" on page 10,
 * and of its solo rules, pages 2 and 3.
 *
 * <ul>
 *   <li>Hadara is for two to five players, and one with the solo variant.
 *   <li>Six things score: colonies, statues, silver medals, gold medals, cards, and every 5
 *       remaining coins. The most points wins. A gold medal scores 7 points for each set of five
 *       differently-colored cards, so that row is always a multiple of 7.
 *   <li>"In the event of a tie, the tied player with the most leftover coins wins. If there is
 *       still a tie, the tied players share the victory." The pad holds the points for coins,
 *       not the coins left over, so nothing that is entered can decide a tie, and the recorder
 *       is asked who won it.
 *   <li>A Solo Play is played against a Non-Player Character, whose points are counted as
 *       usual and entered in a row of their own. The solo rules otherwise follow "all of the
 *       normal game rules", and the NPC "never gains coins": a Player tied with the NPC wins
 *       the tie with a coin left over, and shares the victory without one. Either way, a tie
 *       is won.
 *   <li>The NPC's difficulty, levels 1 to 11 or "God mode", is a Variant of Solo Plays only.
 *   <li>Side A or B of the setup cards changes nothing about the scoring and is not recorded.
 * </ul>
 */
public final class HadaraGameModule extends StandardGameModule {

    /** Stored with every Play recorded through this module. Never change it (ADR 0012). */
    public static final UUID ID = UUID.fromString("b088343a-8984-4a4c-baea-9fdaa9dcdff7");

    public static final String KEY = "hadara";

    /** The Variant "the NPC's difficulty", in a Solo Play: {@code 1} to {@code 11}, or {@code godMode}. */
    public static final String NPC_LEVEL = "npcLevel";

    /** The row for the NPC's score, in a Solo Play. */
    public static final String NPC = "npc";

    private static final ScorePad PAD = new ScorePad(
            1,
            5,
            List.of(new Variant.Choice(
                            NPC_LEVEL, List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "godMode"))
                    .inSoloPlaysOnly()),
            // In the order of "Game End and Final Scoring".
            List.of(
                    ScoreCategory.of("colonies"),
                    ScoreCategory.of("statues"),
                    ScoreCategory.of("silverMedals"),
                    ScoreCategory.of("goldMedals").inStepsOf(7),
                    ScoreCategory.of("cards"),
                    ScoreCategory.of("remainingCoins"),
                    ScoreCategory.of(NPC).notCounted().inSoloPlaysOnly()),
            List.of(),
            true,
            Solo.againstOpponent(NPC, true));

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
}
