package io.github.oliverjuhasz.boardkeeper.gamemodules.sevenwondersduel;

import io.github.oliverjuhasz.boardkeeper.gameapi.Expansion;
import io.github.oliverjuhasz.boardkeeper.gameapi.ScoreCategory;
import io.github.oliverjuhasz.boardkeeper.gameapi.ScorePad;
import io.github.oliverjuhasz.boardkeeper.gameapi.StandardGameModule;
import io.github.oliverjuhasz.boardkeeper.gameapi.TieBreaker;
import io.github.oliverjuhasz.boardkeeper.gameapi.VictoryType;
import java.util.List;
import java.util.UUID;

/**
 * The Game Module for 7 Wonders Duel (Repos Production, 2015), with its Pantheon and Agora
 * expansions.
 *
 * <p>The rules are those of the publisher's rulebooks: the base game's "End of game and victory"
 * on pages 12 and 13, Pantheon's setup and end of game on pages 7 and 10, and Agora's end of the
 * game on page 13. The rows follow the score pads of the base game and of the two expansions.
 *
 * <ul>
 *   <li>7 Wonders Duel is for two players.
 *   <li>It is won in one of three ways. "If you gather 6 different scientific symbols, you
 *       immediately win the game", and "If the Conflict pawn reach the space of your opponent's
 *       capital, you immediately win the game." Otherwise "the player who has the most victory
 *       points wins": a civilian victory. Agora adds a fourth: "if you control all 6 Chambers of
 *       the Senate, you immediately win the game via Political Supremacy." A supremacy ends the
 *       game before anyone counts points, so it is recorded with its winner only.
 *   <li>On points, the rows of the score pad add up: the Buildings by colour, the Wonders, the
 *       Progress tokens, the coins (one point per set of three) and the military track (0, 2, 5
 *       or 10 points).
 *   <li>Pantheon returns the Guilds to the box and puts Grand Temples in their place, which score
 *       5, 12 or 21 points for one, two or three of them. The points its Divinities give have a
 *       row of their own. Agora adds the points of the Chambers of the Senate a player controls.
 *   <li>"In the case where both players are tied, the player who has the most victory points from
 *       their Civilian Buildings (blue cards) wins the game. If this is also a tie, then both
 *       players share their victory." Agora's rulebook says the same.
 *   <li>The promotional Wonders, such as the Statue of Liberty, change nothing on the pad.
 * </ul>
 */
public final class SevenWondersDuelGameModule extends StandardGameModule {

    /** Stored with every Play recorded through this module. Never change it (ADR 0012). */
    public static final UUID ID = UUID.fromString("9e74ba91-e100-4b33-85d8-2d021f61eb38");

    public static final String KEY = "seven-wonders-duel";

    /** The Pantheon expansion. */
    public static final String PANTHEON = "pantheon";

    /** The Agora expansion. */
    public static final String AGORA = "agora";

    /** The row of the Civilian Buildings, the blue cards, which also breaks a tie. */
    public static final String CIVILIAN = "civilian";

    private static final ScorePad PAD = new ScorePad(
            2,
            2,
            // No Variants.
            List.of(),
            // In the order of the score pads.
            List.of(
                    ScoreCategory.of(CIVILIAN),
                    ScoreCategory.of("scientific"),
                    ScoreCategory.of("commercial"),
                    ScoreCategory.of("guilds").onlyWhen(PANTHEON, Expansion.WITHOUT),
                    ScoreCategory.of("grandTemples").atMost(21).onlyWhen(PANTHEON, Expansion.WITH),
                    ScoreCategory.of("divinities").onlyWhen(PANTHEON, Expansion.WITH),
                    ScoreCategory.of("wonders"),
                    ScoreCategory.of("progressTokens"),
                    ScoreCategory.of("coins"),
                    ScoreCategory.of("military").atMost(10),
                    ScoreCategory.of("senate").onlyWhen(AGORA, Expansion.WITH)),
            List.of(TieBreaker.most(CIVILIAN)),
            // What is left after the blue cards is a shared victory.
            false,
            // Not played alone.
            null,
            List.of(new Expansion(PANTHEON), new Expansion(AGORA)),
            List.of(
                    VictoryType.onPoints("civilian"),
                    VictoryType.outright("militarySupremacy"),
                    VictoryType.outright("scientificSupremacy"),
                    VictoryType.outright("politicalSupremacy").onlyWhen(AGORA, Expansion.WITH)));

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
