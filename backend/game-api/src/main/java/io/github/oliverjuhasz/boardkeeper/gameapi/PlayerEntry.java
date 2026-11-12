package io.github.oliverjuhasz.boardkeeper.gameapi;

import java.util.Map;

/**
 * What was entered for one Player in a Play.
 *
 * @param scores the value entered for each Score Category, by the category's key. The keys are
 *     the module's own. Empty when no scores were recorded.
 * @param winner whether the recorder marked this Player as a winner. With Recording Mode
 *     "Winner only" that is the whole Result. With "Points" it is the recorder's choice of
 *     Tie-breaker, for Games that leave a tie to the people at the table.
 */
public record PlayerEntry(Map<String, Integer> scores, boolean winner) {

    public PlayerEntry {
        scores = Map.copyOf(scores);
    }
}
