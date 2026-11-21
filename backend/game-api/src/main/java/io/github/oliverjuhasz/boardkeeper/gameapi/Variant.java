package io.github.oliverjuhasz.boardkeeper.gameapi;

import java.util.List;

/**
 * A setup option of a Game that is chosen for each Play and can change how it is scored. What
 * was chosen is part of a Play's Setup, as a text under the Variant's key.
 *
 * <p>There are three kinds: an on/off switch, a choice between options, and a number in a range.
 * A Variant can depend on another choice of the Setup, such as an adversary's level, which is
 * only chosen once there is an adversary.
 */
public sealed interface Variant {

    /** The Variant's name within its module: lower camel case, such as {@code family}. */
    String key();

    /** What the Setup holds for this Variant when the recorder chose nothing. */
    String defaultValue();

    /**
     * The Setup that makes this Variant a choice at all; null when it always is. A Setup in which
     * it doesn't hold leaves the Variant out.
     */
    default OnlyWhen onlyWhen() {
        return null;
    }

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
     * <pre>{@code
     * new Variant.Choice("difficulty", List.of("novice", "normal", "difficult", "heroic")).byDefault("normal")
     * }</pre>
     *
     * @param options the keys of the options, in the order to offer them
     * @param defaultOption the option when nothing was chosen; the first, unless {@link #byDefault}
     *     names another
     * @param soloOnly see {@link Variant#soloOnly()}
     */
    record Choice(String key, List<String> options, String defaultOption, boolean soloOnly) implements Variant {

        public Choice {
            options = List.copyOf(options);
            if (options.size() < 2) {
                throw new IllegalArgumentException("The Variant '" + key + "' needs at least two options");
            }
            if (!options.contains(defaultOption)) {
                throw new IllegalArgumentException("The default of the Variant '" + key + "' has to be one of its options");
            }
        }

        /** A choice whose first option is the default. */
        public Choice(String key, List<String> options) {
            this(key, options, options.isEmpty() ? null : options.getFirst(), false);
        }

        /** The same choice, with this option as the default instead. */
        public Choice byDefault(String option) {
            return new Choice(key, options, option, soloOnly);
        }

        /** The same choice, made for Solo Plays only. */
        public Choice inSoloPlaysOnly() {
            return new Choice(key, options, defaultOption, true);
        }

        @Override
        public String defaultValue() {
            return defaultOption;
        }

        @Override
        public boolean allows(String value) {
            return options.contains(value);
        }
    }

    /**
     * A whole number in a range, such as an adversary's level from 0 to 6. In a Setup it is the
     * number as text, such as {@code "3"}. The module's texts may name single numbers, such as
     * {@code <module>.variant.<key>.0} for "Base level".
     *
     * @param defaultNumber the number when nothing was chosen
     * @param soloOnly see {@link Variant#soloOnly()}
     * @param onlyWhen see {@link Variant#onlyWhen()}
     */
    record Range(String key, int min, int max, int defaultNumber, boolean soloOnly, OnlyWhen onlyWhen)
            implements Variant {

        public Range {
            if (max <= min || defaultNumber < min || defaultNumber > max) {
                throw new IllegalArgumentException("The Variant '" + key + "' needs a range, and its default in it");
            }
        }

        /** The numbers from {@code min} to {@code max}, the lowest one by default. */
        public Range(String key, int min, int max) {
            this(key, min, max, min, false, null);
        }

        /** The same range, chosen only when the Setup has this value for this Variant or Expansion. */
        public Range onlyWhen(String setupKey, String value) {
            return new Range(key, min, max, defaultNumber, soloOnly, new OnlyWhen(setupKey, value));
        }

        @Override
        public String defaultValue() {
            return String.valueOf(defaultNumber);
        }

        @Override
        public boolean allows(String value) {
            if (value == null || !value.matches("-?\\d{1,9}")) {
                return false;
            }
            int number = Integer.parseInt(value);
            return number >= min && number <= max;
        }
    }
}
