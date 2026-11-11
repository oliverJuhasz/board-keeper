package io.github.oliverjuhasz.boardkeeper.games;

import io.github.oliverjuhasz.boardkeeper.gameapi.RecordingMode;
import java.util.UUID;

/**
 * A board game in the catalogue, with the Game Module that scores it and how its Plays are
 * recorded unless the recorder chooses otherwise.
 */
public record Game(UUID id, String name, UUID gameModuleId, RecordingDefaults defaults) {

    /**
     * How a Game's Plays are recorded by default.
     *
     * @param cooperative everyone wins or loses together, against the game
     * @param recordingMode scores, or only who won
     * @param lowestScoreWins for a competitive Play recorded in points, whether the lowest score
     *     wins instead of the highest
     */
    public record RecordingDefaults(boolean cooperative, RecordingMode recordingMode, boolean lowestScoreWins) {

        /** "Lowest score wins" only means something for a competitive Play recorded in points. */
        public RecordingDefaults {
            if (cooperative || recordingMode == RecordingMode.WINNER_ONLY) {
                lowestScoreWins = false;
            }
        }
    }
}
