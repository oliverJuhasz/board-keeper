package io.github.oliverjuhasz.boardkeeper.gameapi;

/**
 * The Setup that puts something on a score pad: the Variant or Expansion with this key has this
 * value. An Expansion's value is {@code "true"} when it was played with, and {@code "false"}
 * when not.
 *
 * <p>Score Categories and Victory Types use it, for example a row that only the Pantheon
 * expansion adds to the pad.
 *
 * @param key the key of a Variant or an Expansion of the same pad
 * @param value the value it has to have
 */
public record OnlyWhen(String key, String value) {}
