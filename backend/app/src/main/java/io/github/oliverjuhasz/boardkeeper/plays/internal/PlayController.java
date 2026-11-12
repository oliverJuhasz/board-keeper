package io.github.oliverjuhasz.boardkeeper.plays.internal;

import io.github.oliverjuhasz.boardkeeper.access.CurrentAccount;
import io.github.oliverjuhasz.boardkeeper.access.Permission;
import io.github.oliverjuhasz.boardkeeper.access.RequiresPermission;
import io.github.oliverjuhasz.boardkeeper.gameapi.GroupEntry;
import io.github.oliverjuhasz.boardkeeper.gameapi.PlayEntry;
import io.github.oliverjuhasz.boardkeeper.gameapi.PlayerEntry;
import io.github.oliverjuhasz.boardkeeper.gameapi.RecordingMode;
import io.github.oliverjuhasz.boardkeeper.games.Game;
import io.github.oliverjuhasz.boardkeeper.games.Games;
import io.github.oliverjuhasz.boardkeeper.players.Player;
import io.github.oliverjuhasz.boardkeeper.players.Players;
import io.github.oliverjuhasz.boardkeeper.plays.NewPlay;
import io.github.oliverjuhasz.boardkeeper.plays.Play;
import io.github.oliverjuhasz.boardkeeper.plays.Plays;
import io.github.oliverjuhasz.boardkeeper.problems.Problem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/plays")
@Tag(name = "Plays")
class PlayController {

    private final Plays plays;
    private final Games games;
    private final Players players;

    PlayController(Plays plays, Games games, Players players) {
        this.plays = plays;
        this.games = games;
        this.players = players;
    }

    @PostMapping
    @RequiresPermission(Permission.CREATE_PLAYS)
    @Operation(
            summary = "Record a Play",
            description = "The Game's Game Module works out the Results from what is entered. What it refuses is"
                    + " answered with the code plays.invalidEntry and the module's reason.",
            operationId = "recordPlay")
    PlayResponse record(
            @AuthenticationPrincipal CurrentAccount account, @Valid @RequestBody NewPlayRequest body) {
        return toResponse(plays.record(body.toNewPlay(), account));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Show a Play with its Results", operationId = "getPlay")
    PlayResponse get(@PathVariable UUID id) {
        return toResponse(plays.find(id).orElseThrow(() -> Problem.notFound("plays.notFound")));
    }

    /** Adds the names the frontend shows: of the Game, of the Players and of who recorded it. */
    private PlayResponse toResponse(Play play) {
        Game game = games.find(play.gameId()).orElseThrow();
        Set<UUID> playerIds = new HashSet<>();
        play.results().forEach(result -> playerIds.add(result.playerId()));
        playerIds.add(play.recordedBy());
        Map<UUID, Player> playersById = players.find(playerIds);

        return new PlayResponse(
                play.id(),
                new NamedReference(game.id(), game.name()),
                play.playedOn(),
                play.location(),
                play.notes(),
                play.recordingMode(),
                play.setup(),
                play.cooperative(),
                play.group() == null
                        ? null
                        : new GroupResultResponse(
                                play.group().won(), play.group().scores(), play.group().total()),
                play.results().stream()
                        .map(result -> new ResultResponse(
                                NamedReference.to(playersById.get(result.playerId())),
                                result.scores(),
                                result.markedWinner(),
                                result.total(),
                                result.placing(),
                                result.won()))
                        .toList(),
                NamedReference.to(playersById.get(play.recordedBy())),
                play.recordedAt());
    }

    @Schema(
            name = "NewPlay",
            requiredProperties = {"gameId", "playedOn", "recordingMode", "setup", "players"})
    record NewPlayRequest(
            @NotNull UUID gameId,
            @NotNull @Schema(description = "The day it was played. Not later than today.") LocalDate playedOn,
            @Size(max = Plays.MAX_LOCATION_LENGTH) @Schema(description = "Where it was played, in your own words")
                    String location,
            @Size(max = Plays.MAX_NOTES_LENGTH) String notes,
            @NotNull RecordingMode recordingMode,
            @NotNull
                    @Schema(
                            description = "The Setup: the value chosen for each Variant, by the Variant's key. The keys"
                                    + " are the Game Module's. The Generic Module has the on/off Variants"
                                    + " \"cooperative\" and \"lowestScoreWins\", each \"true\" or \"false\".")
                    Map<String, @NotNull String> setup,
            @NotEmpty
                    @Size(max = Plays.MAX_PLAYERS)
                    @Schema(description = "Who played, in the order you want them listed")
                    List<@NotNull @Valid PlayerEntryRequest> players,
            @Valid @Schema(description = "For a Cooperative Play: how it ended for the group") GroupEntryRequest group) {

        NewPlay toNewPlay() {
            return new NewPlay(
                    gameId,
                    playedOn,
                    location,
                    notes,
                    players.stream().map(PlayerEntryRequest::playerId).toList(),
                    new PlayEntry(
                            recordingMode,
                            setup,
                            players.stream()
                                    .map(player -> new PlayerEntry(orEmpty(player.scores()), player.winner()))
                                    .toList(),
                            group == null ? null : new GroupEntry(group.won(), orEmpty(group.scores()))));
        }
    }

    @Schema(
            name = "PlayerEntry",
            requiredProperties = {"playerId", "winner"})
    record PlayerEntryRequest(
            @NotNull UUID playerId,
            @Schema(
                            description = "The value for each Score Category, by the category's key. The Generic Module"
                                    + " has one, \"total\". Leave it out when only the winner is recorded.")
                    Map<String, @NotNull Integer> scores,
            @Schema(
                            description = "Whether you mark this Player as a winner. With Recording Mode WINNER_ONLY that"
                                    + " is the Result. With POINTS it decides a tie for first place.")
                    boolean winner) {}

    @Schema(
            name = "GroupEntry",
            requiredProperties = {"won"})
    record GroupEntryRequest(
            boolean won,
            @Schema(description = "The group's value for each Score Category. The Generic Module has one, \"total\".")
                    Map<String, @NotNull Integer> scores) {}

    @Schema(
            name = "Play",
            requiredProperties = {
                "id",
                "game",
                "playedOn",
                "recordingMode",
                "setup",
                "cooperative",
                "results",
                "recordedBy",
                "recordedAt"
            })
    record PlayResponse(
            UUID id,
            NamedReference game,
            LocalDate playedOn,
            String location,
            String notes,
            RecordingMode recordingMode,
            Map<String, String> setup,
            @Schema(description = "Whether the Players won or lost together") boolean cooperative,
            @Schema(description = "For a Cooperative Play: how it ended for the group") GroupResultResponse group,
            @Schema(description = "One per Player, in the order they were entered") List<ResultResponse> results,
            @Schema(description = "The Player of the Account that recorded it") NamedReference recordedBy,
            Instant recordedAt) {}

    @Schema(
            name = "Result",
            description = "What one Player got in a Play",
            requiredProperties = {"player", "scores", "markedWinner", "won"})
    record ResultResponse(
            NamedReference player,
            @Schema(description = "As entered: the value for each Score Category") Map<String, Integer> scores,
            @Schema(description = "As entered: whether the recorder marked the Player as a winner") boolean markedWinner,
            @Schema(description = "Absent when no scores were recorded for the Player") Integer total,
            @Schema(description = "Absent in a Cooperative Play, and when only the winner was recorded") Integer placing,
            boolean won) {}

    @Schema(
            name = "GroupResult",
            requiredProperties = {"won", "scores"})
    record GroupResultResponse(
            boolean won,
            @Schema(description = "As entered: the group's value for each Score Category") Map<String, Integer> scores,
            @Schema(description = "Absent when no score was recorded") Integer total) {}

    @Schema(
            name = "NamedReference",
            description = "A Game or a Player, with the name to show for it",
            requiredProperties = {"id", "name"})
    record NamedReference(UUID id, String name) {

        static NamedReference to(Player player) {
            return new NamedReference(player.id(), player.name());
        }
    }

    private static <T> Map<String, T> orEmpty(Map<String, T> map) {
        return map == null ? Map.of() : map;
    }
}
