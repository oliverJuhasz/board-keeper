package io.github.oliverjuhasz.boardkeeper.gameapi;

/**
 * An Expansion of a Game that a Play can be played with or without, such as Pantheon for 7
 * Wonders Duel. Whether it was played with is part of a Play's Setup, under the Expansion's key,
 * as {@code "true"} or {@code "false"}. A Setup without it means it was not played with.
 *
 * <p>What an Expansion changes on the score pad is said by the rows and Victory Types that
 * depend on it, through {@link OnlyWhen}.
 *
 * @param key the Expansion's name within its module: lower camel case, such as {@code pantheon}
 */
public record Expansion(String key) {

    public static final String WITH = "true";
    public static final String WITHOUT = "false";

    /** Whether a Setup may hold this value for an Expansion. */
    public static boolean allows(String value) {
        return WITH.equals(value) || WITHOUT.equals(value);
    }
}
