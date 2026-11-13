package io.github.oliverjuhasz.boardkeeper.gameapi;

/**
 * One way a Game puts Players with equal totals in order: by what was entered in a Score
 * Category. The category may or may not count toward the total.
 *
 * @param category the key of the Score Category to compare
 * @param fewestWins whether the lower value wins the tie, instead of the higher
 */
public record TieBreaker(String category, boolean fewestWins) {

    /** The Player with the most in this Score Category wins the tie. */
    public static TieBreaker most(String category) {
        return new TieBreaker(category, false);
    }

    /** The Player with the fewest in this Score Category wins the tie. */
    public static TieBreaker fewest(String category) {
        return new TieBreaker(category, true);
    }
}
