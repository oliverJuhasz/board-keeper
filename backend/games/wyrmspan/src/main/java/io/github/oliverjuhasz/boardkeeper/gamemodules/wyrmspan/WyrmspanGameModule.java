package io.github.oliverjuhasz.boardkeeper.gamemodules.wyrmspan;

import io.github.oliverjuhasz.boardkeeper.gameapi.ScoreCategory;
import io.github.oliverjuhasz.boardkeeper.gameapi.ScorePad;
import io.github.oliverjuhasz.boardkeeper.gameapi.Solo;
import io.github.oliverjuhasz.boardkeeper.gameapi.StandardGameModule;
import io.github.oliverjuhasz.boardkeeper.gameapi.Variant;
import java.util.List;
import java.util.UUID;

/**
 * The Game Module for Wyrmspan (Stonemaier Games, 2024).
 *
 * <p>The rules are those of the publisher's rulebook, "Game End and Scoring" on page 15, its
 * score pad, and the Automa rulebook, "Game End and Scoring" on page 5.
 *
 * <ul>
 *   <li>Wyrmspan is for one to five players.
 *   <li>The score pad has eight rows, from the markers on the Dragon Guild down to the coins and
 *       items left at the end. The most points wins.
 *   <li>"If tied, the player with the most visible dragons on their player mat (not including
 *       tucked cards) wins. If still tied, the victory is shared." The pad does not hold the
 *       number of dragons, so nothing that is entered can decide a tie, and the recorder is
 *       asked who won it.
 *   <li>A Solo Play is played against the Automa, whose score is worked out from its own rules
 *       and entered in a row of its own. "In the event of a tie, you win!"
 *   <li>The Automa has three difficulty levels, and the Ravel Mode variant has three levels of
 *       its own. Both are Variants of Solo Plays only, recorded as one level and an on/off
 *       switch for Ravel Mode.
 * </ul>
 */
public final class WyrmspanGameModule extends StandardGameModule {

    /** Stored with every Play recorded through this module. Never change it (ADR 0012). */
    public static final UUID ID = UUID.fromString("19fc28f0-730d-49aa-8157-730bf2ee89dc");

    public static final String KEY = "wyrmspan";

    /** The Variant "the Automa's difficulty level", in a Solo Play: {@code 1}, {@code 2} or {@code 3}. */
    public static final String AUTOMA_LEVEL = "automaLevel";

    /** The on/off Variant "Ravel Mode", in a Solo Play. */
    public static final String RAVEL_MODE = "ravelMode";

    /** The row for the Automa's score, in a Solo Play. */
    public static final String AUTOMA = "automa";

    private static final ScorePad PAD = new ScorePad(
            1,
            5,
            List.of(
                    new Variant.Choice(AUTOMA_LEVEL, List.of("1", "2", "3")).inSoloPlaysOnly(),
                    new Variant.OnOff(RAVEL_MODE, false).inSoloPlaysOnly()),
            // In the order of the score pad.
            List.of(
                    ScoreCategory.of("dragonGuild"),
                    ScoreCategory.of("printedDragons"),
                    ScoreCategory.of("endGameAbilities"),
                    ScoreCategory.of("eggs"),
                    ScoreCategory.of("cachedResources"),
                    ScoreCategory.of("tuckedCards"),
                    ScoreCategory.of("publicObjectives"),
                    ScoreCategory.of("coinsAndItems"),
                    ScoreCategory.of(AUTOMA).notCounted().inSoloPlaysOnly()),
            List.of(),
            true,
            Solo.againstOpponent(AUTOMA, true));

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
