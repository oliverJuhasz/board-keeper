package io.github.oliverjuhasz.boardkeeper.plays;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * One page of the play history: Plays from the newest day back, and within a day from the last
 * one recorded.
 *
 * @param next where this page ended, to ask for the page after it; null when this was the last
 */
public record PlayHistory(List<Play> plays, Position next) {

    /**
     * Which Plays to list. Whatever is null or empty does not narrow the list.
     *
     * @param playerIds only Plays that every one of these Players took part in
     * @param from only Plays on this day or later
     * @param to only Plays on this day or earlier
     */
    public record Filter(UUID gameId, Set<UUID> playerIds, LocalDate from, LocalDate to) {

        public Filter {
            playerIds = playerIds == null ? Set.of() : Set.copyOf(playerIds);
        }
    }

    /**
     * A place in the history: the last Play of a page. The next page starts right after it.
     *
     * <p>Pointing at a Play, instead of saying "skip the first 50", keeps the pages right while
     * Plays are being added or deleted, and stays fast however long the history gets.
     */
    public record Position(LocalDate playedOn, UUID id) {

        /** Reads the text that {@link #toString()} wrote; empty when it is anything else. */
        public static Optional<Position> parse(String text) {
            String[] parts = text.split("_", 2);
            if (parts.length != 2) {
                return Optional.empty();
            }
            try {
                return Optional.of(new Position(LocalDate.parse(parts[0]), UUID.fromString(parts[1])));
            } catch (DateTimeParseException | IllegalArgumentException e) {
                return Optional.empty();
            }
        }

        @Override
        public String toString() {
            return playedOn + "_" + id;
        }
    }
}
