package io.github.oliverjuhasz.boardkeeper.gamemodules.calico;

import io.github.oliverjuhasz.boardkeeper.gameapi.ScoreCategory;
import io.github.oliverjuhasz.boardkeeper.gameapi.ScorePad;
import io.github.oliverjuhasz.boardkeeper.gameapi.StandardGameModule;
import io.github.oliverjuhasz.boardkeeper.gameapi.Variant;
import java.util.List;
import java.util.UUID;

/**
 * The Game Module for Calico (Flatout Games and AEG, 2020).
 *
 * <p>The rules are those of the publisher's rulebook, "Game End + Final Scoring" and "Variants"
 * on page 8:
 *
 * <ul>
 *   <li>The score pad has three rows: Design Goal Tiles, Cat Tokens and Button Tokens. The most
 *       points wins.
 *   <li>Every button, the rainbow button included, is worth 3 points, so the buttons row goes
 *       up in threes.
 *   <li>"In case of a tie, the player with the most cat tokens is the winner. If still tied,
 *       the player with the most button tokens is the winner. If still tied, the victory is
 *       shared!" The pad holds the cats' points, not how many cat tokens there are, and cats
 *       are worth different amounts. So nothing that is entered can decide a tie, and the
 *       recorder is asked who won it.
 *   <li>In the family variant the Design Goal Tiles lie face down and "will not be scored", so
 *       their row is off the pad.
 * </ul>
 *
 * <p>The rulebook's other options change nothing about the scoring and are not recorded: the
 * beginner setup, and the lower variance variant for two players.
 *
 * <p>Calico is for one to four players. This module records Plays with two to four: how a Solo
 * Play is recorded is settled with the solo styles of milestone M3.
 */
public final class CalicoGameModule extends StandardGameModule {

    /** Stored with every Play recorded through this module. Never change it (ADR 0012). */
    public static final UUID ID = UUID.fromString("d392b4e5-a531-44c4-85c7-369783d9be5c");

    public static final String KEY = "calico";

    /** The on/off Variant "family variant". */
    public static final String FAMILY = "family";

    private static final ScorePad PAD = new ScorePad(
            2,
            4,
            List.of(new Variant.OnOff(FAMILY, false)),
            List.of(
                    ScoreCategory.of("designGoals").onlyWhen(FAMILY, "false"),
                    ScoreCategory.of("cats"),
                    ScoreCategory.of("buttons").inStepsOf(3)),
            List.of(),
            true);

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
