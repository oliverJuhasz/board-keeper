package io.github.oliverjuhasz.boardkeeper.games.internal;

import io.github.oliverjuhasz.boardkeeper.access.Permission;
import io.github.oliverjuhasz.boardkeeper.access.RequiresPermission;
import io.github.oliverjuhasz.boardkeeper.gameapi.RecordingMode;
import io.github.oliverjuhasz.boardkeeper.games.Game;
import io.github.oliverjuhasz.boardkeeper.games.Game.RecordingDefaults;
import io.github.oliverjuhasz.boardkeeper.games.Games;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/games")
@Tag(name = "Games")
class GameController {

    private final Games games;

    GameController(Games games) {
        this.games = games;
    }

    @GetMapping
    @Operation(summary = "List the Games in the catalogue by name", operationId = "listGames")
    List<GameResponse> list() {
        return games.all().stream().map(this::toResponse).toList();
    }

    @PostMapping
    @RequiresPermission(Permission.CREATE_GAMES)
    @Operation(
            summary = "Add a Game to the catalogue",
            description = "A Game added this way is scored by the Generic Module.",
            operationId = "createGame")
    GameResponse create(@Valid @RequestBody GameRequest body) {
        return toResponse(games.create(body.name(), body.defaults()));
    }

    @PutMapping("/{id}")
    @RequiresPermission(Permission.EDIT_GAMES)
    @Operation(
            summary = "Rename a Game and change how its Plays are recorded",
            description = "Not for a Game with a Game Module of its own.",
            operationId = "updateGame")
    GameResponse update(@PathVariable UUID id, @Valid @RequestBody GameRequest body) {
        return toResponse(games.update(id, body.name(), body.defaults()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(Permission.REMOVE_GAMES)
    @Operation(
            summary = "Remove a Game",
            description = "Only a Game without Plays and without a Game Module of its own can be removed.",
            operationId = "removeGame")
    void remove(@PathVariable UUID id) {
        games.remove(id);
    }

    @Schema(requiredProperties = {"name", "cooperative", "recordingMode", "lowestScoreWins"})
    record GameRequest(
            @NotBlank @Size(max = Games.MAX_NAME_LENGTH) String name,
            @Schema(description = "Everyone wins or loses together, against the game") boolean cooperative,
            @NotNull RecordingMode recordingMode,
            @Schema(description = "For a competitive Play recorded in points: the lowest score wins")
                    boolean lowestScoreWins) {

        RecordingDefaults defaults() {
            return new RecordingDefaults(cooperative, recordingMode, lowestScoreWins);
        }
    }

    private GameResponse toResponse(Game game) {
        return new GameResponse(
                game.id(),
                game.name(),
                game.gameModuleId(),
                games.moduleKeyOf(game).orElse(null),
                games.hasOwnModule(game),
                game.defaults().cooperative(),
                game.defaults().recordingMode(),
                game.defaults().lowestScoreWins());
    }

    @Schema(
            name = "Game",
            requiredProperties = {
                "id",
                "name",
                "gameModuleId",
                "hasOwnModule",
                "cooperative",
                "recordingMode",
                "lowestScoreWins"
            })
    record GameResponse(
            UUID id,
            @Schema(description = "The name as stored. A Game with a module of its own is shown under the name in the"
                            + " module's texts instead: \"<moduleKey>.name\".")
                    String name,
            UUID gameModuleId,
            @Schema(description = "The key of the Game Module that scores the Game, such as \"calico\". Absent when"
                            + " that module is not installed.")
                    String moduleKey,
            @Schema(description = "Whether the Game has a Game Module of its own. Such a Game cannot be edited or"
                            + " removed.")
                    boolean hasOwnModule,
            @Schema(description = "For a Game scored by the Generic Module: how its Plays are recorded by default")
                    boolean cooperative,
            RecordingMode recordingMode,
            boolean lowestScoreWins) {}
}
