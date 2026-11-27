package io.github.oliverjuhasz.boardkeeper.gamemodules.lastbastion;

import io.github.oliverjuhasz.boardkeeper.gameapi.CooperativeGameModule;
import io.github.oliverjuhasz.boardkeeper.gameapi.InvalidPlayException;
import io.github.oliverjuhasz.boardkeeper.gameapi.PlayerSetupChoice;
import io.github.oliverjuhasz.boardkeeper.gameapi.RecordingMode;
import io.github.oliverjuhasz.boardkeeper.gameapi.ScoreCategory;
import io.github.oliverjuhasz.boardkeeper.gameapi.ScorePad;
import io.github.oliverjuhasz.boardkeeper.gameapi.Variant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Game Module for Last Bastion (Repos Production, 2019).
 *
 * <p>The rules are those of the publisher's rulebook: "Preamble" on page 2, "End of the game" and
 * "Score" on page 10, and "Games with less than 4 players" and "Difficulty levels" on page 11.
 * The Heroes are those of the Power Description booklet, page 1.
 *
 * <ul>
 *   <li>Last Bastion is a cooperative game for one to four players: "Either they all win
 *       victory, or else they all suffer defeat." Each player chooses a Hero, and each Hero is
 *       played by one player at most.
 *   <li>It is played at one of four difficulty levels: Novice, Normal, Difficult and Heroic. The
 *       rulebook suggests Normal for the first games, so it is the default.
 *   <li>The box has a pad of score sheets, so a Play starts in points. Only a victory is scored,
 *       as Board Keeper reads the rulebook: "5 points for a victory in Novice mode", 10 in
 *       Normal, 15 in Difficult and 20 in Heroic, "+ 1 point per Life token in your possession",
 *       "+ 2 points per Call to Arms token in your possession", "- 3 points per dead Hero" and
 *       "- 4 points per Bastion tile in the Grasp of Evil". A defeat has no rows and no score.
 *   <li>The game is lost when "a third Bastion tile falls under the Grasp of Evil", so after a
 *       victory there are at most two. It is also lost when "all of the heroes are taken out",
 *       and a dead Hero never comes back, so at least one Hero is alive after a victory: there
 *       are fewer dead Heroes than players.
 * </ul>
 */
public final class LastBastionGameModule extends CooperativeGameModule {

    /** Stored with every Play recorded through this module. Never change it (ADR 0012). */
    public static final UUID ID = UUID.fromString("e91e79b5-9868-4f88-af26-521b30849941");

    public static final String KEY = "last-bastion";

    /** The Variant "difficulty level": {@code novice}, {@code normal}, {@code difficult} or {@code heroic}. */
    public static final String DIFFICULTY = "difficulty";

    /** The points for a victory at each difficulty level. */
    static final Map<String, Integer> VICTORY_POINTS = Map.of("novice", 5, "normal", 10, "difficult", 15, "heroic", 20);

    private static final ScorePad PAD = ScorePad.cooperative(
            1,
            4,
            // No Expansions.
            List.of(),
            List.of(new Variant.Choice(DIFFICULTY, List.of("novice", "normal", "difficult", "heroic"))
                    .byDefault("normal")),
            // The group's rows, counted after a victory. A defeat has no score.
            List.of(
                    ScoreCategory.of("lifeTokens").onlyAfterVictory(),
                    ScoreCategory.of("callToArmsTokens").onlyAfterVictory(),
                    ScoreCategory.of("deadHeroes").atMost(3).onlyAfterVictory(),
                    ScoreCategory.of("graspOfEvilTiles").atMost(2).onlyAfterVictory()),
            List.of(PlayerSetupChoice.of(
                            "hero",
                            List.of(
                                    "elmerAndChestnut",
                                    "mylfaar",
                                    "arneAndSoko",
                                    "sindara",
                                    "teagaan",
                                    "fengLi",
                                    "kazgin",
                                    "twindalli"))
                    .eachOptionOncePerPlay()),
            RecordingMode.POINTS);

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

    /** Called after a victory only: a defeat has no rows, and so no score. */
    @Override
    protected int groupScore(Map<String, String> setup, boolean won, Map<String, Integer> rows, int players) {
        int deadHeroes = rows.get("deadHeroes");
        if (deadHeroes >= players) {
            throw new InvalidPlayException("tooManyDeadHeroes");
        }
        return VICTORY_POINTS.get(PAD.valueOf(DIFFICULTY, setup))
                + rows.get("lifeTokens")
                + 2 * rows.get("callToArmsTokens")
                - 3 * deadHeroes
                - 4 * rows.get("graspOfEvilTiles");
    }
}
