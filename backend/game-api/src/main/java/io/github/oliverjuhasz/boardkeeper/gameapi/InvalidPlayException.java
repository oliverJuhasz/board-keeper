package io.github.oliverjuhasz.boardkeeper.gameapi;

/**
 * Thrown by {@link GameModule#score} when what was entered for a Play breaks the Game's rules.
 *
 * <p>A module says why with a short reason of its own, such as {@code winnerMissing}, and
 * explains it to the user in its translation files, under {@code <key>.invalid.<reason>}:
 * {@code generic.invalid.winnerMissing} for the Generic Module.
 *
 * <p>{@link StandardGameModule} refuses for reasons that are the same for every Game, such as
 * too few Players. Those are {@link Standard} reasons, and the frontend has the texts for them,
 * so that no module has to repeat them.
 */
public class InvalidPlayException extends RuntimeException {

    /** The reasons that mean the same for every Game. */
    public enum Standard {
        /** Fewer Players than the Game can be played with. */
        TOO_FEW_PLAYERS("tooFewPlayers"),
        /** More Players than the Game can be played with. */
        TOO_MANY_PLAYERS("tooManyPlayers"),
        /** A Player has no value in a Score Category that is on the pad. */
        SCORE_MISSING("scoreMissing"),
        /** A value is lower or higher than its Score Category allows, or not one of its steps. */
        SCORE_NOT_ALLOWED("scoreNotAllowed"),
        /** Only the winner is recorded, and nobody was marked as one. */
        WINNER_MISSING("winnerMissing"),
        /** A Player was marked as the winner of a tie they are not part of. */
        WINNER_NOT_TIED("winnerNotTied"),
        /** Something was entered that this kind of Play does not record. */
        NOT_RECORDED("notRecorded"),
        /** A Solo Play for score only was recorded without its scores. */
        SCORES_NEEDED("scoresNeeded"),
        /** A Play of a Game that can be won in more than one way does not say how it was won. */
        VICTORY_TYPE_MISSING("victoryTypeMissing"),
        /** A Play won outright, not on points, has more than one winner. */
        ONE_WINNER_ONLY("oneWinnerOnly"),
        /** Two Players have the same option of a Player Setup choice that can be made once per Play. */
        CHOSEN_TWICE("chosenTwice");

        private final String reason;

        Standard(String reason) {
            this.reason = reason;
        }
    }

    private final String reason;
    private final boolean standard;

    /** A refusal for a reason of the module's own. */
    public InvalidPlayException(String reason) {
        super(reason);
        this.reason = reason;
        this.standard = false;
    }

    /** A refusal for a reason that is the same for every Game. */
    public InvalidPlayException(Standard reason) {
        super(reason.reason);
        this.reason = reason.reason;
        this.standard = true;
    }

    public String reason() {
        return reason;
    }

    /** Whether the reason is one of the {@link Standard} ones, which the frontend has the texts for. */
    public boolean isStandard() {
        return standard;
    }
}
