package io.github.oliverjuhasz.boardkeeper.gameapi;

/**
 * How a Solo Play is recorded, for a Game that can be played alone. A score pad without one is
 * for two Players or more.
 *
 * <p>Like {@link Outcome}, it is {@code sealed}: these two records are the only kinds there are.
 */
public sealed interface Solo {

    /**
     * The Player plays for a score, as in Calico and Harmonies. A Solo Play is then neither won
     * nor lost, and has no Placing.
     */
    static Solo forScoreOnly() {
        return new ForScoreOnly();
    }

    /**
     * The Player plays against an automated opponent, as against the Automa in Wyrmspan, and
     * wins or loses. The opponent's score is entered in a Score Category of its own, which only
     * Solo Plays have and which does not count toward the Player's total.
     *
     * @param opponentCategory the key of that Score Category
     * @param tieWins whether the Player wins when their total equals the opponent's score
     */
    static Solo againstOpponent(String opponentCategory, boolean tieWins) {
        return new AgainstOpponent(opponentCategory, tieWins);
    }

    /** See {@link Solo#forScoreOnly()}. */
    record ForScoreOnly() implements Solo {}

    /** See {@link Solo#againstOpponent(String, boolean)}. */
    record AgainstOpponent(String opponentCategory, boolean tieWins) implements Solo {}
}
