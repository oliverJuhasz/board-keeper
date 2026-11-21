package io.github.oliverjuhasz.boardkeeper.gameapi;

/**
 * An Expansion of a Game that a Play can be played with or without, such as Pantheon for 7
 * Wonders Duel. Whether it was played with is part of a Play's Setup, under the Expansion's key,
 * as {@code "true"} or {@code "false"}. A Setup without it means it was not played with.
 *
 * <p>An Expansion declares what it changes, so that the frontend can follow the rules as the
 * Setup is chosen, and the module checks a Play against the same description (ADR 0019). It can
 * let more Players take part ({@link #allowingUpTo}). The rows, Victory Types and Player Setup
 * options it brings say so themselves: a row through {@link OnlyWhen}, a Player Setup option
 * through {@link PlayerSetupChoice#onlyWith}.
 *
 * @param key the Expansion's name within its module: lower camel case, such as {@code pantheon}
 * @param maxPlayers the most Players a Play with this Expansion can have, more than the score
 *     pad's own maximum; null when the Expansion doesn't change it
 */
public record Expansion(String key, Integer maxPlayers) {

    public static final String WITH = "true";
    public static final String WITHOUT = "false";

    public Expansion {
        if (maxPlayers != null && maxPlayers < 1) {
            throw new IllegalArgumentException("The Expansion '" + key + "' is for at least one Player");
        }
    }

    /** An Expansion that doesn't change how many can play. */
    public Expansion(String key) {
        this(key, null);
    }

    /**
     * The same Expansion, with which a Play can have up to this many Players, as six can play
     * Lords of Waterdeep with Scoundrels of Skullport. It has to be more than the score pad's own
     * maximum.
     */
    public Expansion allowingUpTo(int players) {
        return new Expansion(key, players);
    }

    /** Whether a Setup may hold this value for an Expansion. */
    public static boolean allows(String value) {
        return WITH.equals(value) || WITHOUT.equals(value);
    }
}
