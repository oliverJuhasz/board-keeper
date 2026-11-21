package io.github.oliverjuhasz.boardkeeper.gameapi;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One choice of a Player Setup: something chosen for each Player in a Play, such as their Wonder
 * in 7 Wonders. A Player's Player Setup holds the key of the chosen option under the choice's
 * key. A choice may be left out, for a Player whose choice nobody remembers.
 *
 * <p>Start from {@link #of(String, List)} and change what differs:
 *
 * <pre>{@code
 * PlayerSetupChoice.of("wonder", List.of("alexandria", "babylon", "roma"))
 *         .eachOptionOncePerPlay()
 *         .onlyWith("leaders", List.of("roma"))
 * }</pre>
 *
 * @param key the choice's name within its module: lower camel case, such as {@code wonder}
 * @param options the keys of the options, in the order to offer them
 * @param oncePerPlay whether each option can be chosen for one Player of a Play at most, as each
 *     Wonder board exists only once
 * @param optionsOnlyWith the options that come with an Expansion, each with that Expansion's key.
 *     Such an option can only be chosen in a Play with that Expansion. Every other option always
 *     can.
 */
public record PlayerSetupChoice(String key, List<String> options, boolean oncePerPlay, Map<String, String> optionsOnlyWith) {

    public PlayerSetupChoice {
        options = List.copyOf(options);
        optionsOnlyWith = Map.copyOf(optionsOnlyWith);
        if (options.size() < 2) {
            throw new IllegalArgumentException("The Player Setup choice '" + key + "' needs at least two options");
        }
        if (!options.containsAll(optionsOnlyWith.keySet())) {
            throw new IllegalArgumentException(
                    "The Player Setup choice '" + key + "' ties an option it doesn't have to an Expansion");
        }
    }

    public static PlayerSetupChoice of(String key, List<String> options) {
        return new PlayerSetupChoice(key, options, false, Map.of());
    }

    /** The same choice, where no two Players of a Play can have the same option. */
    public PlayerSetupChoice eachOptionOncePerPlay() {
        return new PlayerSetupChoice(key, options, true, optionsOnlyWith);
    }

    /**
     * The same choice, where these of its options come with an Expansion, as Roma comes with
     * Leaders in 7 Wonders: they can only be chosen in a Play with it.
     *
     * @param expansion the key of an Expansion of the same score pad
     */
    public PlayerSetupChoice onlyWith(String expansion, List<String> options) {
        Map<String, String> onlyWith = new HashMap<>(optionsOnlyWith);
        options.forEach(option -> onlyWith.put(option, expansion));
        return new PlayerSetupChoice(key, this.options, oncePerPlay, onlyWith);
    }

    /** The options that can be chosen in a Play with this Setup, in the order to offer them. */
    public List<String> optionsFor(ScorePad pad, Map<String, String> setup) {
        return options.stream()
                .filter(option -> !optionsOnlyWith.containsKey(option)
                        || Expansion.WITH.equals(pad.valueOf(optionsOnlyWith.get(option), setup)))
                .toList();
    }
}
