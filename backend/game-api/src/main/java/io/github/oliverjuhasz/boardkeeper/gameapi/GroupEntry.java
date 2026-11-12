package io.github.oliverjuhasz.boardkeeper.gameapi;

import java.util.Map;

/**
 * What was entered for the group as a whole in a Cooperative Play.
 *
 * @param won whether the group won against the game
 * @param scores the value entered for each of the group's Score Categories, by the category's
 *     key. Empty when no scores were recorded.
 */
public record GroupEntry(boolean won, Map<String, Integer> scores) {

    public GroupEntry {
        scores = Map.copyOf(scores);
    }
}
