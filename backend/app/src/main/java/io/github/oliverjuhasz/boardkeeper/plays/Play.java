package io.github.oliverjuhasz.boardkeeper.plays;

import io.github.oliverjuhasz.boardkeeper.gameapi.RecordingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One occasion on which a group of Players played a Game to the end. It holds what the recorder
 * entered and what the Game Module calculated from that when the Play was recorded (ADR 0010).
 *
 * @param gameModuleId the Game Module that scored this Play
 * @param setup the Setup: the value chosen for each Variant, by the Variant's key
 * @param cooperative whether the Players won or lost together
 * @param group how it ended for the group, which only a Cooperative Play has; otherwise null
 * @param results one per Player, in the order the recorder entered them
 * @param recordedBy the Player of the Account that recorded it
 */
public record Play(
        UUID id,
        UUID gameId,
        UUID gameModuleId,
        LocalDate playedOn,
        String location,
        String notes,
        RecordingMode recordingMode,
        Map<String, String> setup,
        boolean cooperative,
        GroupResult group,
        List<Result> results,
        UUID recordedBy,
        Instant recordedAt) {

    /** A Solo Play: one Player on their own, in a Game that isn't played cooperatively. */
    public boolean solo() {
        return !cooperative && results.size() == 1;
    }

    /**
     * What one Player got in a Play.
     *
     * @param scores entered: the value for each Score Category, by the category's key
     * @param markedWinner entered: whether the recorder marked the Player as a winner
     * @param total calculated: null when no scores were recorded for the Player
     * @param placing calculated: null in a Cooperative Play and in a Solo Play, and when only the
     *     winner was recorded
     * @param won calculated: in a Cooperative Play, the same for every Player; null in a Solo Play
     *     recorded for score only, which nobody wins or loses
     */
    public record Result(
            UUID playerId, Map<String, Integer> scores, boolean markedWinner, Integer total, Integer placing, Boolean won) {}

    /**
     * How a Cooperative Play ended for the group.
     *
     * @param scores entered: the value for each of the group's Score Categories
     * @param total calculated: null when no score was recorded
     */
    public record GroupResult(boolean won, Map<String, Integer> scores, Integer total) {}
}
