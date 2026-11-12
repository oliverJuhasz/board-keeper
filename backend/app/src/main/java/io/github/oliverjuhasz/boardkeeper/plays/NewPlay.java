package io.github.oliverjuhasz.boardkeeper.plays;

import io.github.oliverjuhasz.boardkeeper.gameapi.PlayEntry;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A Play as its recorder describes it, before it is scored and stored.
 *
 * @param location where it was played, in the recorder's own words; null when not given
 * @param notes anything the recorder wants to remember about it; null when not given
 * @param playerIds who played, in the order the recorder entered them
 * @param entry what was entered about how it went, for the Game Module to score. Its
 *     {@code players} are in the same order as {@code playerIds}.
 */
public record NewPlay(
        UUID gameId, LocalDate playedOn, String location, String notes, List<UUID> playerIds, PlayEntry entry) {

    public NewPlay {
        location = tidy(location);
        notes = tidy(notes);
        playerIds = List.copyOf(playerIds);
        if (playerIds.size() != entry.players().size()) {
            throw new IllegalArgumentException("A Play needs exactly one entry for each of its Players");
        }
    }

    /** Without spaces around it; null when nothing is left. */
    private static String tidy(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
