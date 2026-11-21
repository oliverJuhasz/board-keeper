package io.github.oliverjuhasz.boardkeeper.exports.internal;

import io.github.oliverjuhasz.boardkeeper.players.Players;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * What an Import exchanges with the frontend (ADR 0021): the review of an Export before anything is
 * written, the Admin's choice of who each of its Players is, and what the Import did.
 */
final class Import {

    private Import() {}

    @Schema(
            name = "ImportReview",
            description = "What importing an Export would do. Nothing has been written yet.",
            requiredProperties = {"games", "players", "plays", "alreadyHere", "refused"})
    record Review(
            @Schema(description = "Each of the file's Games, and where its Plays would go") List<ReviewedGame> games,
            @Schema(description = "Each of the file's Players, with who they most likely are here")
                    List<ReviewedPlayer> players,
            @Schema(description = "How many Plays the file holds") int plays,
            @Schema(description = "How many of them are here already, and would be left as they are") int alreadyHere,
            @Schema(description = "The Plays this Installation can't take, and why") List<RefusedPlay> refused) {}

    /** Where the Plays of one of the file's Games go. */
    enum GameMatch {
        /** To a Game in the catalogue: the same Game Module's, or the one with the Game's name. */
        CATALOGUE,
        /** To a new Game, scored by the Generic Module, which the Import adds to the catalogue. */
        NEW,
        /** Nowhere: the Game Module that scores the Game isn't installed here. */
        MODULE_MISSING
    }

    @Schema(
            name = "ImportReviewedGame",
            requiredProperties = {"fileId", "name", "match"})
    record ReviewedGame(
            @Schema(description = "The Game's id in the file") UUID fileId,
            @Schema(description = "Its name in the file") String name,
            GameMatch match,
            @Schema(description = "For CATALOGUE: the Game here") UUID gameId,
            @Schema(description = "For MODULE_MISSING: the key of the Game Module it needs, or its id when the file"
                            + " doesn't say")
                    String gameModuleKey) {}

    @Schema(
            name = "ImportReviewedPlayer",
            requiredProperties = {"fileId", "name"})
    record ReviewedPlayer(
            @Schema(description = "The Player's id in the file") UUID fileId,
            String name,
            @Schema(description = "Who the Player most likely is here: the Player in their place in a Play that is"
                            + " here already, or else the Player with the same name, whatever its capitals. Absent"
                            + " when there is neither, in which case a new Player is the obvious choice.")
                    UUID suggestedPlayerId) {}

    @Schema(
            name = "ImportRefusedPlay",
            requiredProperties = {"playId", "playedOn", "game", "reason"})
    record RefusedPlay(
            UUID playId,
            LocalDate playedOn,
            @Schema(description = "The name of its Game in the file") String game,
            @Schema(description = "Why, as the name of a text: under \"problems\" in the frontend's texts, or a Game"
                            + " Module's own such as \"<module>.invalid.<reason>\"")
                    String reason,
            @Schema(description = "When the reason is imports.gameModuleMissing: the key of the Game Module, or its id"
                            + " when the file doesn't say")
                    String gameModuleKey) {}

    @Schema(
            name = "ImportRequest",
            requiredProperties = {"file", "players"})
    record Request(
            @NotNull @Valid @Schema(description = "The Export, as it was reviewed") Export file,
            @NotNull
                    @Schema(description = "Who each of the file's Players is here: one choice for every one of them")
                    List<@NotNull @Valid PlayerChoice> players) {}

    @Schema(
            name = "ImportPlayerChoice",
            description = "Either a Player here, or the name of a new Player",
            requiredProperties = {"fileId"})
    record PlayerChoice(
            @NotNull @Schema(description = "The Player's id in the file") UUID fileId,
            @Schema(description = "The Player here who this is. Each can be chosen for one of the file's Players only.")
                    UUID playerId,
            @Size(max = Players.MAX_NAME_LENGTH)
                    @Schema(description = "A new Player's name, which no Player here may have yet")
                    String newName) {}

    @Schema(
            name = "ImportResult",
            requiredProperties = {"added", "alreadyHere", "refused", "newPlayers", "newGames"})
    record Result(
            @Schema(description = "How many Plays were added") int added,
            @Schema(description = "How many Plays were here already, and were left as they are") int alreadyHere,
            @Schema(description = "The Plays this Installation couldn't take, and why") List<RefusedPlay> refused,
            int newPlayers,
            int newGames) {}
}
