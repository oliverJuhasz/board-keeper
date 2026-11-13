package io.github.oliverjuhasz.boardkeeper.gamemodules.harmonies;

import io.github.oliverjuhasz.boardkeeper.gameapi.ScoreCategory;
import io.github.oliverjuhasz.boardkeeper.gameapi.ScorePad;
import io.github.oliverjuhasz.boardkeeper.gameapi.Solo;
import io.github.oliverjuhasz.boardkeeper.gameapi.StandardGameModule;
import io.github.oliverjuhasz.boardkeeper.gameapi.Variant;
import java.util.List;
import java.util.UUID;

/**
 * The Game Module for Harmonies (Libellud, 2024).
 *
 * <p>The rules are those of the publisher's rulebook: "End of game" on page 7, "Tallying points"
 * on pages 8 and 9, and "Nature's Spirit cards" on page 10.
 *
 * <ul>
 *   <li>Points come from the Landscapes a Player created (Trees, Mountains, Fields, Buildings
 *       and Water) and from their Animal cards. The most points wins.
 *   <li>A Field and a Building score 5 points each, so those rows go up in fives.
 *   <li>Water depends on the side of the Personal boards, which all Players share. On side A it
 *       is the River, scored by its length. On side B it is the Islands, 5 points each, and
 *       "you always have at least 1 island". The two are separate rows here, and the Variant
 *       for the side puts the right one on the pad.
 *   <li>The paper pad has a line for each Animal card. Players hold different numbers of
 *       cards, so this pad has one row for the sum of them.
 *   <li>With the Nature's Spirit cards, each Player has one such card. Its points "are scored
 *       just like Animal cards, and are in addition to all other points". It has a row of its
 *       own, which the Variant for these cards puts on the pad.
 *   <li>"In case of a tie, the player who has placed the most Animal cubes wins. In the event of
 *       another tie, players share the victory." The pad holds points, not the number of cubes,
 *       so nothing that is entered can decide a tie, and the recorder is asked who won it.
 * </ul>
 *
 * <p>Harmonies is for one to four players. In the solo mode (page 11) a Player plays for a score
 * and earns suns for it instead of winning, so a Solo Play is recorded for score only. The suns
 * are not recorded.
 */
public final class HarmoniesGameModule extends StandardGameModule {

    /** Stored with every Play recorded through this module. Never change it (ADR 0012). */
    public static final UUID ID = UUID.fromString("229df245-d2c8-4657-b781-be9b41279f42");

    public static final String KEY = "harmonies";

    /** The Variant "side of the Personal boards": {@code a} for the River, {@code b} for the Islands. */
    public static final String BOARD_SIDE = "boardSide";

    /** The on/off Variant "Nature's Spirit cards". */
    public static final String NATURES_SPIRIT = "naturesSpirit";

    private static final ScorePad PAD = new ScorePad(
            1,
            4,
            // The rulebook recommends side A for the first games, so it is the default.
            List.of(new Variant.Choice(BOARD_SIDE, List.of("a", "b")), new Variant.OnOff(NATURES_SPIRIT, false)),
            List.of(
                    ScoreCategory.of("trees"),
                    ScoreCategory.of("mountains"),
                    ScoreCategory.of("fields").inStepsOf(5),
                    ScoreCategory.of("buildings").inStepsOf(5),
                    ScoreCategory.of("river").onlyWhen(BOARD_SIDE, "a"),
                    ScoreCategory.of("islands").inStepsOf(5).atLeast(5).onlyWhen(BOARD_SIDE, "b"),
                    ScoreCategory.of("animals"),
                    ScoreCategory.of("naturesSpirit").onlyWhen(NATURES_SPIRIT, "true")),
            List.of(),
            true,
            Solo.forScoreOnly());

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
