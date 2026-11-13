package io.github.oliverjuhasz.boardkeeper.gameapi;

import java.util.List;

/**
 * A setup option of a Game that is chosen for each Play and can change how it is scored. What
 * was chosen is part of a Play's Setup, as a text under the Variant's key.
 *
 * <p>There are two kinds so far. A number in a range, such as an adversary's level, comes with
 * the first Game that needs one.
 */
public sealed interface Variant {

    /** The Variant's name within its module: lower camel case, such as {@code family}. */
    String key();

    /** What the Setup holds for this Variant when the recorder chose nothing. */
    String defaultValue();

    /** Whether a Setup may hold this value for the Variant. */
    boolean allows(String value);

    /**
     * Whether the Variant is only chosen for a Solo Play, such as the Automa's level in
     * Wyrmspan. Only a score pad with a {@link Solo} has such Variants.
     */
    boolean soloOnly();

    /**
     * A switch, such as Calico's family variant. In a Setup it is {@code "true"} or {@code "false"}.
     *
     * @param soloOnly see {@link Variant#soloOnly()}
     */
    record OnOff(String key, boolean onByDefault, boolean soloOnly) implements Variant {

        public OnOff(String key, boolean onByDefault) {
            this(key, onByDefault, false);
        }

        /** The same switch, chosen for Solo Plays only. */
        public OnOff inSoloPlaysOnly() {
            return new OnOff(key, onByDefault, true);
        }

        @Override
        public String defaultValue() {
            return String.valueOf(onByDefault);
        }

        @Override
        public boolean allows(String value) {
            return "true".equals(value) || "false".equals(value);
        }
    }

    /**
     * A choice between options, such as the side of a board. In a Setup it is the key of the
     * chosen option.
     *
     * @param options the keys of the options, in the order to offer them; the first is the default
     * @param soloOnly see {@link Variant#soloOnly()}
     */
    record Choice(String key, List<String> options, boolean soloOnly) implements Variant {

        public Choice {
            options = List.copyOf(options);
            if (options.size() < 2) {
                throw new IllegalArgumentException("The Variant '" + key + "' needs at least two options");
            }
        }

        public Choice(String key, List<String> options) {
            this(key, options, false);
        }

        /** The same choice, made for Solo Plays only. */
        public Choice inSoloPlaysOnly() {
            return new Choice(key, options, true);
        }

        @Override
        public String defaultValue() {
            return options.getFirst();
        }

        @Override
        public boolean allows(String value) {
            return options.contains(value);
        }
    }
}
