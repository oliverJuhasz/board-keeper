package io.github.oliverjuhasz.boardkeeper.gameapi;

import java.util.List;

/**
 * One choice of a Player Setup: something chosen for each Player in a Play, such as their Wonder
 * in 7 Wonders. A Player's Player Setup holds the key of the chosen option under the choice's
 * key. A choice may be left out, for a Player whose choice nobody remembers.
 *
 * <p>Start from {@link #of(String, List)} and change what differs:
 *
 * <pre>{@code
 * PlayerSetupChoice.of("wonder", List.of("alexandria", "babylon")).eachOptionOncePerPlay()
 * }</pre>
 *
 * @param key the choice's name within its module: lower camel case, such as {@code wonder}
 * @param options the keys of the options, in the order to offer them
 * @param oncePerPlay whether each option can be chosen for one Player of a Play at most, as each
 *     Wonder board exists only once
 */
public record PlayerSetupChoice(String key, List<String> options, boolean oncePerPlay) {

    public PlayerSetupChoice {
        options = List.copyOf(options);
        if (options.size() < 2) {
            throw new IllegalArgumentException("The Player Setup choice '" + key + "' needs at least two options");
        }
    }

    public static PlayerSetupChoice of(String key, List<String> options) {
        return new PlayerSetupChoice(key, options, false);
    }

    /** The same choice, where no two Players of a Play can have the same option. */
    public PlayerSetupChoice eachOptionOncePerPlay() {
        return new PlayerSetupChoice(key, options, true);
    }
}
