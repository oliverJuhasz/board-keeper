package io.github.oliverjuhasz.boardkeeper.gameapi;

/**
 * Thrown by {@link GameModule#score} when what was entered for a Play breaks the Game's rules.
 *
 * <p>The module says why with a short reason of its own, such as {@code winnerMissing}, and
 * explains it to the user in its translation files, under {@code <key>.invalid.<reason>}:
 * {@code generic.invalid.winnerMissing} for the Generic Module.
 */
public class InvalidPlayException extends RuntimeException {

    private final String reason;

    public InvalidPlayException(String reason) {
        super(reason);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
