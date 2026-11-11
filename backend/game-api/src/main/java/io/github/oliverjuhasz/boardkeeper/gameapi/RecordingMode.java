package io.github.oliverjuhasz.boardkeeper.gameapi;

/** How much of a Play's outcome is recorded. */
public enum RecordingMode {
    /** Scores: one per Player, or one for the whole group in a Cooperative Play. */
    POINTS,
    /** Only who won. */
    WINNER_ONLY
}
