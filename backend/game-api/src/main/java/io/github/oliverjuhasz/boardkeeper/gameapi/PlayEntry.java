package io.github.oliverjuhasz.boardkeeper.gameapi;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Everything that was entered for a Play and that its Game Module needs to score it. The core
 * stores it as entered, next to the {@link Outcome} the module calculated from it.
 *
 * <p>Who the Players are, and when and where they played, is the core's business and is not
 * part of the entry.
 *
 * @param recordingMode whether scores were entered, or only who won
 * @param setup the Setup: the value chosen for each Variant and whether each Expansion was played
 *     with, by their keys. An on/off Variant and an Expansion are {@code "true"} or
 *     {@code "false"}. The keys are the module's own.
 * @param players what was entered for each Player, in the order they were entered
 * @param group what was entered for the group as a whole, which only a Cooperative Play has;
 *     otherwise null
 * @param victoryType the key of the Victory Type the Play was won by, for a Game that can be won
 *     in more than one way; otherwise null
 */
public record PlayEntry(
        RecordingMode recordingMode,
        Map<String, String> setup,
        List<PlayerEntry> players,
        GroupEntry group,
        String victoryType) {

    public PlayEntry {
        Objects.requireNonNull(recordingMode, "recordingMode");
        setup = Map.copyOf(setup);
        players = List.copyOf(players);
    }

    /** The entry for a Play of a Game that is won in one way only. */
    public PlayEntry(RecordingMode recordingMode, Map<String, String> setup, List<PlayerEntry> players, GroupEntry group) {
        this(recordingMode, setup, players, group, null);
    }
}
