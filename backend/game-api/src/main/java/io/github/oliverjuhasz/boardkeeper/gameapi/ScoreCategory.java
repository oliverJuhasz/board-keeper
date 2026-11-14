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
 * ScoreCategory.of("automa").notCounted().inSoloPlaysOnly()
 * }</pre>
 *
 * @param key the category's name within its module: lower camel case, such as {@code designGoals}
 * @param countsTowardTotal false for a value that is only entered to break ties
 * @param min the lowest value allowed; null when there is none
 * @param max the highest value allowed; null when there is none
 * @param step every value has to be a multiple of this: 3 for a row where each token is worth 3
 * @param onlyWhen the Setup that puts this row on the pad: a Variant's or an Expansion's value;
 *     null for a row that is always on it
 * @param soloOnly whether the row is only on the pad of a Solo Play, such as the Automa's score
 *     in Wyrmspan
 */
public record ScoreCategory(
        String key,
        boolean countsTowardTotal,
        Integer min,
        Integer max,
        int step,
        OnlyWhen onlyWhen,
        boolean soloOnly) {

    public ScoreCategory {
        if (step < 1) {
            throw new IllegalArgumentException("The step of the Score Category '" + key + "' must be at least 1");
        }
    }

    public static ScoreCategory of(String key) {
        return new ScoreCategory(key, true, 0, null, 1, null, false);
    }

    /** A value that is entered but not added to the total, for example because it only breaks ties. */
    public ScoreCategory notCounted() {
        return new ScoreCategory(key, false, min, max, step, onlyWhen, soloOnly);
    }

    public ScoreCategory mayBeNegative() {
        return new ScoreCategory(key, countsTowardTotal, null, max, step, onlyWhen, soloOnly);
    }

    /** For a row that always scores something, such as Harmonies' islands: there is at least one. */
    public ScoreCategory atLeast(int lowest) {
        return new ScoreCategory(key, countsTowardTotal, lowest, max, step, onlyWhen, soloOnly);
    }

    public ScoreCategory atMost(int highest) {
        return new ScoreCategory(key, countsTowardTotal, min, highest, step, onlyWhen, soloOnly);
    }

    public ScoreCategory inStepsOf(int multiple) {
        return new ScoreCategory(key, countsTowardTotal, min, max, multiple, onlyWhen, soloOnly);
    }

    /**
     * Puts the row on the pad only for Plays whose Setup has this value for this Variant or
     * Expansion. For an Expansion, the value is {@link Expansion#WITH} or {@link Expansion#WITHOUT}.
     */
    public ScoreCategory onlyWhen(String setupKey, String value) {
        return new ScoreCategory(key, countsTowardTotal, min, max, step, new OnlyWhen(setupKey, value), soloOnly);
    }

    /** Puts the row on the pad of Solo Plays only. */
    public ScoreCategory inSoloPlaysOnly() {
        return new ScoreCategory(key, countsTowardTotal, min, max, step, onlyWhen, true);
    }

    /** Whether a value may be entered in this row. */
    public boolean allows(int value) {
        return (min == null || value >= min) && (max == null || value <= max) && value % step == 0;
    }
}
