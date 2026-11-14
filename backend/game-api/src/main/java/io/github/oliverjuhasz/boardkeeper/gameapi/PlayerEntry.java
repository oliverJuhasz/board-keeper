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
 * @param setup the Player's Player Setup: the option chosen for each of the pad's Player Setup
 *     choices, by the choice's key. A choice that was not made is left out.
 */
public record PlayerEntry(Map<String, Integer> scores, boolean winner, Map<String, String> setup) {

    public PlayerEntry {
        scores = Map.copyOf(scores);
        setup = Map.copyOf(setup);
    }

    /** What was entered for a Player of a Game without Player Setup. */
    public PlayerEntry(Map<String, Integer> scores, boolean winner) {
        this(scores, winner, Map.of());
    }
}
