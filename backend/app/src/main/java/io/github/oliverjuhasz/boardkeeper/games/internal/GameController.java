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
        return games.all().stream().map(GameResponse::from).toList();
    }

    @PostMapping
    @RequiresPermission(Permission.CREATE_GAMES)
    @Operation(
            summary = "Add a Game to the catalogue",
            description = "A Game added this way is scored by the Generic Module.",
            operationId = "createGame")
    GameResponse create(@Valid @RequestBody GameRequest body) {
        return GameResponse.from(games.create(body.name(), body.defaults()));
    }

    @PutMapping("/{id}")
    @RequiresPermission(Permission.EDIT_GAMES)
    @Operation(summary = "Rename a Game and change how its Plays are recorded", operationId = "updateGame")
    GameResponse update(@PathVariable UUID id, @Valid @RequestBody GameRequest body) {
        return GameResponse.from(games.update(id, body.name(), body.defaults()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(Permission.REMOVE_GAMES)
    @Operation(
            summary = "Remove a Game",
            description = "Only a Game without Plays can be removed.",
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

    @Schema(
            name = "Game",
            requiredProperties = {"id", "name", "gameModuleId", "cooperative", "recordingMode", "lowestScoreWins"})
    record GameResponse(
            UUID id,
            String name,
            UUID gameModuleId,
            boolean cooperative,
            RecordingMode recordingMode,
            boolean lowestScoreWins) {

        static GameResponse from(Game game) {
            return new GameResponse(
                    game.id(),
                    game.name(),
                    game.gameModuleId(),
                    game.defaults().cooperative(),
                    game.defaults().recordingMode(),
                    game.defaults().lowestScoreWins());
        }
    }
}
