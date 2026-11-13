package io.github.oliverjuhasz.boardkeeper.gameapi;

import java.util.List;

/**
 * How a Play ended, as its Game Module calculated it from the {@link PlayEntry}.
 *
 * <p>A Play is either competitive or Cooperative, and the two end differently. {@code sealed}
 * tells the compiler that these two records are the only kinds of Outcome there are, so a
 * {@code switch} over an Outcome has to handle both and needs no default branch.
 */
public sealed interface Outcome {

    /**
     * The Players competed, so each has a Result of their own.
     *
     * @param results one per Player, in the order of {@link PlayEntry#players()}
     * @param tieForRecorder the Players, by their place in {@link PlayEntry#players()}, among
     *     whom the recorder may say who won: they are level on everything the module can
     *     compare, and the Game decides such a tie by something that was not entered. The
     *     recorder says so by marking winners, and the {@code results} already reflect any
     *     marks. Empty when what was entered decides who won.
     */
    record Competitive(List<PlayerResult> results, List<Integer> tieForRecorder) implements Outcome {

        public Competitive {
            results = List.copyOf(results);
            tieForRecorder = List.copyOf(tieForRecorder);
        }
    }

    /**
     * The Players won or lost together, against the game.
     *
     * @param won whether they won
     * @param total the group's score; null when none was recorded
     */
    record Cooperative(boolean won, Integer total) implements Outcome {}
}
