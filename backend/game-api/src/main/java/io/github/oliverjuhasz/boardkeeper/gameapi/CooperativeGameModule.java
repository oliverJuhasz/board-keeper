package io.github.oliverjuhasz.boardkeeper.gameapi;

import io.github.oliverjuhasz.boardkeeper.gameapi.InvalidPlayException.Standard;
import java.util.List;
import java.util.Map;

/**
 * A Game Module for a Cooperative Game: the Players win or lose together, against the game. The
 * recorder says whether the group won. With Recording Mode "Points" they also fill in the group's
 * rows of the score pad, and the module works out the group's score from them in
 * {@link #groupScore}. Nothing is recorded for the Players one by one, besides their Player
 * Setup.
 *
 * <p>A Game that scores only a victory marks its rows {@link ScoreCategory#onlyAfterVictory()}. A
 * defeat then has no rows on the pad, and no score, even with Recording Mode "Points".
 *
 * <p>Its score pad is made with {@link ScorePad#cooperative}.
 */
public abstract class CooperativeGameModule implements GameModule {

    @Override
    public final Outcome score(PlayEntry entry) {
        ScorePad pad = scorePad();
        if (!pad.cooperative()) {
            throw new IllegalStateException("Game Module '" + key() + "' needs a cooperative score pad");
        }
        List<PlayerEntry> players = entry.players();
        Checks.playerCount(pad, entry.setup(), players.size());
        Checks.setup(pad, entry.setup(), false);
        Checks.playerSetup(pad, entry.setup(), players);
        if (entry.victoryType() != null
                || players.stream().anyMatch(player -> !player.scores().isEmpty() || player.winner())) {
            throw new InvalidPlayException(Standard.NOT_RECORDED);
        }
        GroupEntry group = entry.group();
        if (group == null) {
            throw new InvalidPlayException(Standard.GROUP_RESULT_MISSING);
        }
        if (entry.recordingMode() == RecordingMode.WINNER_ONLY) {
            if (!group.scores().isEmpty()) {
                throw new InvalidPlayException(Standard.NOT_RECORDED);
            }
            return new Outcome.Cooperative(group.won(), null);
        }
        List<ScoreCategory> rows = pad.groupCategoriesFor(entry.setup(), group.won());
        Checks.rows(rows, group.scores());
        if (rows.isEmpty()) {
            // A defeat of a Game that scores only a victory.
            return new Outcome.Cooperative(group.won(), null);
        }
        return new Outcome.Cooperative(group.won(), groupScore(entry.setup(), group.won(), group.scores(), players.size()));
    }

    /**
     * Works out the group's score from its rows of the score pad. Like {@link #score}, it must
     * depend on what is given alone.
     *
     * @param setup the Play's Setup, as entered
     * @param won whether the group won
     * @param rows the value of each of the group's rows on the pad for this Setup and outcome, all of
     *     them filled in. There is at least one: without any, the group has no score.
     * @param players how many Players there were
     */
    protected abstract int groupScore(Map<String, String> setup, boolean won, Map<String, Integer> rows, int players);
}
