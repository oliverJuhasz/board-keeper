package io.github.oliverjuhasz.boardkeeper.gamemodules.lordsofwaterdeep;

import io.github.oliverjuhasz.boardkeeper.gameapi.Expansion;
import io.github.oliverjuhasz.boardkeeper.gameapi.PlayerSetupChoice;
import io.github.oliverjuhasz.boardkeeper.gameapi.ScoreCategory;
import io.github.oliverjuhasz.boardkeeper.gameapi.ScorePad;
import io.github.oliverjuhasz.boardkeeper.gameapi.StandardGameModule;
import io.github.oliverjuhasz.boardkeeper.gameapi.TieBreaker;
import java.util.List;
import java.util.UUID;

/**
 * The Game Module for Lords of Waterdeep (Wizards of the Coast, 2012), with the two halves of its
 * Scoundrels of Skullport expansion, Undermountain and Skullport.
 *
 * <p>The rules are those of the publishers' rulebooks: the base game's "Agents" on page 4,
 * "Lord Cards" on page 7, "Ending the Game" and "Final Scoring" on page 11, and its Lords on
 * pages 20 and 21; Scoundrels of Skullport's "Using this Expansion" on page 4, "Six-Player Games"
 * on page 5 and "Ending the Game" on page 8.
 *
 * <ul>
 *   <li>Lords of Waterdeep is for two to five players. Scoundrels of Skullport "includes two game
 *       modules", Undermountain and Skullport, "each of which can be added to the base game", and
 *       here each is an Expansion of its own. "To play with six players, you must include at least
 *       one expansion module", so either half lets six play.
 *   <li>There is no score pad, so the rows follow "Final Scoring": the Victory Points on the
 *       scoring track at the end of the eighth round, then "each Adventurer in your Tavern" for 1
 *       VP, "every 2 Gold in your Tavern (rounding down)" for 1 VP, and the Lord card, "as
 *       specified by the card".
 *   <li>Skullport adds a row for Corruption: "Each player reduces his or her Victory Point total
 *       by an amount determined by the total Corruption in his or her Tavern", each token being
 *       worth "the number on the empty space of the Corruption track that is farthest from -1".
 *       The rules reference on its back cover says "closest" instead, which its own example on
 *       page 8 contradicts. The row holds the points lost, so it is zero or less.
 *   <li>"In the event of a tie, the player with the most Gold wins." The pad holds the points
 *       for Gold, not the Gold: more points for Gold always means more Gold, so that row breaks a
 *       tie first, and the recorder is asked who won what it leaves.
 *   <li>Each player is secretly one of the Lords of Waterdeep, the Player Setup. The base game
 *       has eleven, and each half of Scoundrels of Skullport brings three more, which can only be
 *       chosen in a Play with it. The rulebook names one of them, The Xanathar; the others are
 *       from the cards. Each Lord is dealt once.
 *   <li>The faction, which is only the colour of a player's pieces, is not recorded, and neither
 *       is the optional long game: neither changes the scoring.
 * </ul>
 */
public final class LordsOfWaterdeepGameModule extends StandardGameModule {

    /** Stored with every Play recorded through this module. Never change it (ADR 0012). */
    public static final UUID ID = UUID.fromString("1352e3c2-fb6b-4be5-bc24-762320ed8894");

    public static final String KEY = "lords-of-waterdeep";

    /** The Undermountain half of Scoundrels of Skullport. */
    public static final String UNDERMOUNTAIN = "undermountain";

    /** The Skullport half of Scoundrels of Skullport. */
    public static final String SKULLPORT = "skullport";

    /** The row of the points for Gold, which also breaks a tie. */
    public static final String GOLD = "gold";

    private static final ScorePad PAD = new ScorePad(
            2,
            5,
            // No Variants.
            List.of(),
            // In the order of "Final Scoring", after the points already on the scoring track.
            List.of(
                    ScoreCategory.of("scoringTrack"),
                    ScoreCategory.of("adventurers"),
                    ScoreCategory.of(GOLD),
                    ScoreCategory.of("lordCard"),
                    ScoreCategory.of("corruption").mayBeNegative().atMost(0).onlyWhen(SKULLPORT, Expansion.WITH)),
            List.of(TieBreaker.most(GOLD)),
            // The Gold itself, which decides what the points for it leave, is not on the pad.
            true,
            // Not played alone.
            null,
            List.of(new Expansion(UNDERMOUNTAIN).allowingUpTo(6), new Expansion(SKULLPORT).allowingUpTo(6)),
            // Won in one way: on points.
            List.of(),
            List.of(PlayerSetupChoice.of(
                            "lord",
                            List.of(
                                    "brianneByndraeth",
                                    "caladornCassalanter",
                                    "durnanTheWanderer",
                                    "khelbenArunsun",
                                    "kyrianiAgrivar",
                                    "larissaNeathal",
                                    "mirtTheMoneylender",
                                    "nindilJalbuck",
                                    "nymaraScheiron",
                                    "piergeironThePaladinson",
                                    "sammerezaSulphontis",
                                    "daniloThann",
                                    "halasterBlackcloak",
                                    "trobriand",
                                    "irusylEraneth",
                                    "sangalor",
                                    "theXanathar"))
                    .eachOptionOncePerPlay()
                    .onlyWith(UNDERMOUNTAIN, List.of("daniloThann", "halasterBlackcloak", "trobriand"))
                    .onlyWith(SKULLPORT, List.of("irusylEraneth", "sangalor", "theXanathar"))));

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
