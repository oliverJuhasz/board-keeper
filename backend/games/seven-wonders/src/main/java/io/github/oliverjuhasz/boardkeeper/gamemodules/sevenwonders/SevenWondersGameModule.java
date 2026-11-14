package io.github.oliverjuhasz.boardkeeper.gamemodules.sevenwonders;

import io.github.oliverjuhasz.boardkeeper.gameapi.Expansion;
import io.github.oliverjuhasz.boardkeeper.gameapi.PlayerSetupChoice;
import io.github.oliverjuhasz.boardkeeper.gameapi.ScoreCategory;
import io.github.oliverjuhasz.boardkeeper.gameapi.ScorePad;
import io.github.oliverjuhasz.boardkeeper.gameapi.StandardGameModule;
import java.util.List;
import java.util.UUID;

/**
 * The Game Module for 7 Wonders, second edition (Repos Production, 2020), with its Leaders and
 * Cities expansions.
 *
 * <p>The rules are those of the publisher's rulebooks: the base game's "End of the game" and
 * "Counting victory points" on page 8, Leaders' "End of the game" on page 4, and Cities' "Debt
 * tokens" on page 3 and "End of the game" on page 5. The rows follow the base game's score pad
 * and the one for the expansions.
 *
 * <ul>
 *   <li>7 Wonders is for three to seven players. The rulebook doesn't say; the box does.
 *   <li>Each player has a Wonder board, played on its Day or Night side. Both are the Player
 *       Setup, and each Wonder exists once. Leaders brings Roma and Abu Simbel, Cities brings
 *       Byzantium and Petra, and Manneken Pis is a promotional Wonder.
 *   <li>The rows of the score pad add up: the Wonder, the Treasury (a point per three coins), the
 *       Military Conflict tokens, and the blue, yellow, green and purple cards. "The total may be
 *       a negative number" for Military, as Defeats are worth -1 each.
 *   <li>Leaders adds a row for the Leaders. Cities adds one for the black cards, and its Debts
 *       lose points: "count your Debts in the 'Coins' line of the score pad", so the Treasury can
 *       be negative too.
 *   <li>"If there is a tie, the player with the most Treasure wins. If there is still a tie, all
 *       tied players share the victory." The pad holds the Treasury's points, not the coins, so
 *       nothing that is entered can decide a tie, and the recorder is asked who won it.
 *   <li>The promotional Guilds and Leaders score in the purple and Leaders rows, so they change
 *       nothing on the pad. The team game of Cities and the Armada expansion are not supported.
 * </ul>
 */
public final class SevenWondersGameModule extends StandardGameModule {

    /** Stored with every Play recorded through this module. Never change it (ADR 0012). */
    public static final UUID ID = UUID.fromString("1adf0579-480f-4598-b5ea-92416b96b555");

    public static final String KEY = "seven-wonders";

    /** The Leaders expansion. */
    public static final String LEADERS = "leaders";

    /** The Cities expansion. */
    public static final String CITIES = "cities";

    private static final ScorePad PAD = new ScorePad(
            3,
            7,
            // No Variants.
            List.of(),
            // In the order of the score pads.
            List.of(
                    ScoreCategory.of("wonder"),
                    ScoreCategory.of("treasury").mayBeNegative(),
                    ScoreCategory.of("military").mayBeNegative(),
                    ScoreCategory.of("civilian"),
                    ScoreCategory.of("commercial"),
                    ScoreCategory.of("scientific"),
                    ScoreCategory.of("guilds"),
                    ScoreCategory.of("cities").onlyWhen(CITIES, Expansion.WITH),
                    ScoreCategory.of("leaders").onlyWhen(LEADERS, Expansion.WITH)),
            // The Treasure that decides a tie is not on the pad.
            List.of(),
            true,
            // Not played alone.
            null,
            List.of(new Expansion(LEADERS), new Expansion(CITIES)),
            // Won in one way: on points.
            List.of(),
            List.of(
                    PlayerSetupChoice.of(
                                    "wonder",
                                    List.of(
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
                                            "mannekenPis"))
                            .eachOptionOncePerPlay(),
                    PlayerSetupChoice.of("side", List.of("day", "night"))));

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
