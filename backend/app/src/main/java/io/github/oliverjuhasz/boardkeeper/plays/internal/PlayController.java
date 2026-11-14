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
import io.github.oliverjuhasz.boardkeeper.plays.PlayHistory;
import io.github.oliverjuhasz.boardkeeper.plays.PlayPreview;
import io.github.oliverjuhasz.boardkeeper.plays.Plays;
import io.github.oliverjuhasz.boardkeeper.problems.Problem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    @GetMapping
    @Operation(
            summary = "List the Plays, newest first",
            description = "The play history, a page at a time. Every filter is optional. To get the page after one,"
                    + " pass its \"next\" as \"after\" and keep the filters the same.",
            operationId = "listPlays")
    PlayHistoryResponse list(
            @AuthenticationPrincipal CurrentAccount account,
            @Parameter(description = "Only Plays of this Game") @RequestParam(required = false) UUID gameId,
            @Parameter(description = "Only Plays this Player took part in. Give it several times for Plays that all"
                            + " of those Players took part in.")
                    @RequestParam(required = false)
                    Set<UUID> playerId,
            @Parameter(description = "Only Plays on this day or later")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @Parameter(description = "Only Plays on this day or earlier")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to,
            @Parameter(description = "The \"next\" of the page before the one you want. Leave it out, or empty, for the first page.")
                    @RequestParam(required = false)
                    String after,
            @Parameter(description = "How many Plays a page has, at most " + Plays.MAX_PAGE_SIZE)
                    @RequestParam(defaultValue = "" + Plays.DEFAULT_PAGE_SIZE)
                    int limit) {
        PlayHistory.Position position = after == null || after.isBlank()
                ? null
                : PlayHistory.Position.parse(after)
                        .orElseThrow(() -> new Problem(HttpStatus.BAD_REQUEST, "request.invalid"));
        PlayHistory history = plays.history(new PlayHistory.Filter(gameId, playerId, from, to), position, limit);
        return new PlayHistoryResponse(
                toResponses(history.plays(), account),
                history.next() == null ? null : history.next().toString());
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
        return toResponse(plays.record(body.id(), body.toNewPlay(), account), account);
    }

    @GetMapping("/last-played")
    @Operation(
            summary = "When each Game was last played",
            description = "For putting the most recently played Games first. A Game without Plays is left out.",
            operationId = "listLastPlayed")
    List<LastPlayedResponse> lastPlayed() {
        return plays.lastPlayedOn().entrySet().stream()
                .map(entry -> new LastPlayedResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    @PostMapping("/preview")
    @Operation(
            summary = "Work out how a Play would end, without recording it",
            description = "For a form that shows totals and Placings while it is filled in. \"tieForRecorder\" names"
                    + " the Players of a tie for first place that the scores cannot decide: ask the recorder who won"
                    + " it, and mark that Player as winner.",
            operationId = "previewPlay")
    PlayPreviewResponse preview(@Valid @RequestBody PlayPreviewRequest body) {
        List<UUID> playerIds =
                body.players().stream().map(PlayerEntryRequest::playerId).toList();
        PlayPreview preview = plays.preview(body.gameModuleId(), playerIds, body.toEntry());
        return new PlayPreviewResponse(
                preview.group() != null,
                preview.group() == null
                        ? null
                        : new GroupResultResponse(
                                preview.group().won(), preview.group().scores(), preview.group().total()),
                preview.results().stream()
                        .map(result ->
                                new PreviewResultResponse(result.playerId(), result.total(), result.placing(), result.won()))
                        .toList(),
                preview.tieForRecorder());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Show a Play with its Results", operationId = "getPlay")
    PlayResponse get(@AuthenticationPrincipal CurrentAccount account, @PathVariable UUID id) {
        return toResponse(plays.find(id).orElseThrow(() -> Problem.notFound("plays.notFound")), account);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Change a Play",
            description = "Replaces everything that was entered for the Play and works out its Results again. The"
                    + " Game has to stay the same. Who may do this depends on the Play: see \"canChange\".",
            operationId = "changePlay")
    PlayResponse change(
            @AuthenticationPrincipal CurrentAccount account,
            @PathVariable UUID id,
            @Valid @RequestBody NewPlayRequest body) {
        return toResponse(plays.change(id, body.toNewPlay(), account), account);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Delete a Play",
            description = "For good. Who may do this depends on the Play: see \"canChange\".",
            operationId = "deletePlay")
    void delete(@AuthenticationPrincipal CurrentAccount account, @PathVariable UUID id) {
        plays.delete(id, account);
    }

    private PlayResponse toResponse(Play play, CurrentAccount account) {
        return toResponses(List.of(play), account).getFirst();
    }

    /**
     * Adds what the frontend shows besides the Plays themselves: the names of the Games, of the
     * Players and of who recorded each Play, and whether the signed-in Account may change it.
     * The names are fetched once for all the Plays together.
     */
    private List<PlayResponse> toResponses(List<Play> plays, CurrentAccount account) {
        Set<UUID> gameIds = new HashSet<>();
        Set<UUID> playerIds = new HashSet<>();
        for (Play play : plays) {
            gameIds.add(play.gameId());
            playerIds.add(play.recordedBy());
            play.results().forEach(result -> playerIds.add(result.playerId()));
        }
        Map<UUID, Game> gamesById = games.find(gameIds);
        Map<UUID, Player> playersById = players.find(playerIds);

        return plays.stream()
                .map(play -> new PlayResponse(
                        play.id(),
                        gameReference(gamesById.get(play.gameId())),
                        play.gameModuleId(),
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
                        play.recordedAt(),
                        this.plays.mayChange(play, account)))
                .toList();
    }

    private GameReference gameReference(Game game) {
        return new GameReference(game.id(), game.name(), games.moduleKeyOf(game).filter(key -> games.hasOwnModule(game)).orElse(null));
    }

    @Schema(
            name = "NewPlay",
            requiredProperties = {"gameId", "playedOn", "recordingMode", "setup", "players"})
    record NewPlayRequest(
            @Schema(description = "Only when recording, and optional: the new Play's id, chosen by the device. A Play"
                            + " is recorded once per id: sending it again gives back the Play already recorded."
                            + " Another Account's Play with the same id is refused with plays.idTaken. Use a UUID"
                            + " of version 7, which starts with the time: the history orders a day's Plays by id.")
                    UUID id,
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
                    entryOf(recordingMode, setup, players, group));
        }
    }

    @Schema(
            name = "PlayPreviewRequest",
            description = "What is entered about how a Play went, with the Game Module to score it",
            requiredProperties = {"gameModuleId", "recordingMode", "setup", "players"})
    record PlayPreviewRequest(
            @NotNull UUID gameModuleId,
            @NotNull RecordingMode recordingMode,
            @NotNull Map<String, @NotNull String> setup,
            @NotNull @Size(max = Plays.MAX_PLAYERS) List<@NotNull @Valid PlayerEntryRequest> players,
            @Valid GroupEntryRequest group) {

        PlayEntry toEntry() {
            return entryOf(recordingMode, setup, players, group);
        }
    }

    private static PlayEntry entryOf(
            RecordingMode recordingMode, Map<String, String> setup, List<PlayerEntryRequest> players, GroupEntryRequest group) {
        return new PlayEntry(
                recordingMode,
                setup,
                players.stream()
                        .map(player -> new PlayerEntry(orEmpty(player.scores()), player.winner()))
                        .toList(),
                group == null ? null : new GroupEntry(group.won(), orEmpty(group.scores())));
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
                "gameModuleId",
                "playedOn",
                "recordingMode",
                "setup",
                "cooperative",
                "results",
                "recordedBy",
                "recordedAt",
                "canChange"
            })
    record PlayResponse(
            UUID id,
            GameReference game,
            @Schema(description = "The Game Module that scored this Play. The keys in \"setup\" and \"scores\" are its."
                            + " It can differ from the module that scores the Game today.")
                    UUID gameModuleId,
            LocalDate playedOn,
            String location,
            String notes,
            RecordingMode recordingMode,
            Map<String, String> setup,
            @Schema(description = "Whether the Players won or lost together") boolean cooperative,
            @Schema(description = "For a Cooperative Play: how it ended for the group") GroupResultResponse group,
            @Schema(description = "One per Player, in the order they were entered") List<ResultResponse> results,
            @Schema(description = "The Player of the Account that recorded it") NamedReference recordedBy,
            Instant recordedAt,
            @Schema(description = "Whether the signed-in Account may edit and delete this Play") boolean canChange) {}

    @Schema(
            name = "PlayHistory",
            requiredProperties = {"plays"})
    record PlayHistoryResponse(
            List<PlayResponse> plays,
            @Schema(description = "Pass this as \"after\" to get the next page. Absent when this is the last page.")
                    String next) {}

    @Schema(
            name = "Result",
            description = "What one Player got in a Play",
            requiredProperties = {"player", "scores", "markedWinner"})
    record ResultResponse(
            NamedReference player,
            @Schema(description = "As entered: the value for each Score Category") Map<String, Integer> scores,
            @Schema(description = "As entered: whether the recorder marked the Player as a winner") boolean markedWinner,
            @Schema(description = "Absent when no scores were recorded for the Player") Integer total,
            @Schema(description = "Absent in a Cooperative Play and a Solo Play, and when only the winner was recorded")
                    Integer placing,
            @Schema(description = "Absent in a Solo Play recorded for score only, which nobody wins or loses")
                    Boolean won) {}

    @Schema(
            name = "GroupResult",
            requiredProperties = {"won", "scores"})
    record GroupResultResponse(
            boolean won,
            @Schema(description = "As entered: the group's value for each Score Category") Map<String, Integer> scores,
            @Schema(description = "Absent when no score was recorded") Integer total) {}

    @Schema(
            name = "LastPlayed",
            requiredProperties = {"gameId", "playedOn"})
    record LastPlayedResponse(UUID gameId, @Schema(description = "The day of the Game's latest Play") LocalDate playedOn) {}

    @Schema(
            name = "PlayPreview",
            requiredProperties = {"cooperative", "results", "tieForRecorder"})
    record PlayPreviewResponse(
            boolean cooperative,
            GroupResultResponse group,
            @Schema(description = "One per Player, in the order they were entered") List<PreviewResultResponse> results,
            @Schema(description = "The Players of a tie for first place that the recorder may decide. Empty when the"
                            + " scores decide who won.")
                    List<UUID> tieForRecorder) {}

    @Schema(
            name = "PreviewResult",
            requiredProperties = {"playerId"})
    record PreviewResultResponse(
            UUID playerId,
            Integer total,
            Integer placing,
            @Schema(description = "Absent in a Solo Play recorded for score only") Boolean won) {}

    @Schema(
            name = "GameReference",
            description = "The Game of a Play, with the name to show for it",
            requiredProperties = {"id", "name"})
    record GameReference(
            UUID id,
            String name,
            @Schema(description = "For a Game with a Game Module of its own: the module's key. The Game is then shown"
                            + " under the name in the module's texts, \"<moduleKey>.name\".")
                    String moduleKey) {}

    @Schema(
            name = "NamedReference",
            description = "A Player, with the name to show for them",
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
