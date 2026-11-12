package io.github.oliverjuhasz.boardkeeper.gameapi;

/**
 * One row of a Game's score pad: a named value entered for each Player, such as "Cats" in
 * Calico.
 *
 * <p>Start from {@link #of(String)}, which gives a row of points that count toward the total
 * and cannot be negative, and change what differs:
 *
 * <pre>{@code
 * ScoreCategory.of("buttons").inStepsOf(3)
 * ScoreCategory.of("designGoals").onlyWhen("family", "false")
 * }</pre>
 *
 * @param key the category's name within its module: lower camel case, such as {@code designGoals}
 * @param countsTowardTotal false for a value that is only entered to break ties
 * @param min the lowest value allowed; null when there is none
 * @param max the highest value allowed; null when there is none
 * @param step every value has to be a multiple of this: 3 for a row where each token is worth 3
 * @param onlyWhen the Variant value that puts this row on the pad; null for a row that is always
 *     on it
 */
public record ScoreCategory(
        String key, boolean countsTowardTotal, Integer min, Integer max, int step, OnlyWhen onlyWhen) {

    /** The Setup that puts a row on the pad: the Variant with this key has this value. */
    public record OnlyWhen(String variant, String value) {}

    public ScoreCategory {
        if (step < 1) {
            throw new IllegalArgumentException("The step of the Score Category '" + key + "' must be at least 1");
        }
    }

    public static ScoreCategory of(String key) {
        return new ScoreCategory(key, true, 0, null, 1, null);
    }

    /** A value that is entered but not added to the total, for example because it only breaks ties. */
    public ScoreCategory notCounted() {
        return new ScoreCategory(key, false, min, max, step, onlyWhen);
    }

    public ScoreCategory mayBeNegative() {
        return new ScoreCategory(key, countsTowardTotal, null, max, step, onlyWhen);
    }

    public ScoreCategory atMost(int highest) {
        return new ScoreCategory(key, countsTowardTotal, min, highest, step, onlyWhen);
    }

    public ScoreCategory inStepsOf(int multiple) {
        return new ScoreCategory(key, countsTowardTotal, min, max, multiple, onlyWhen);
    }

    /** Puts the row on the pad only for Plays whose Setup has this value for this Variant. */
    public ScoreCategory onlyWhen(String variant, String value) {
        return new ScoreCategory(key, countsTowardTotal, min, max, step, new OnlyWhen(variant, value));
    }

    /** Whether a value may be entered in this row. */
    public boolean allows(int value) {
        return (min == null || value >= min) && (max == null || value <= max) && value % step == 0;
    }
}
