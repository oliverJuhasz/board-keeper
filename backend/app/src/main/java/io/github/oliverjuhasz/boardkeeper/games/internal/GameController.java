package io.github.oliverjuhasz.boardkeeper.games.internal;

import io.github.oliverjuhasz.boardkeeper.games.internal.GameRepository.GameRow;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/games")
@Tag(name = "Games")
class GameController {

    private final GameRepository games;

    GameController(GameRepository games) {
        this.games = games;
    }

    @GetMapping
    @Operation(summary = "List the Games in the catalogue", operationId = "listGames")
    List<GameResponse> list() {
        return games.findAllOrderedByName().stream().map(GameResponse::from).toList();
    }

    @Schema(name = "Game", requiredProperties = {"id", "name", "gameModuleId"})
    record GameResponse(UUID id, String name, UUID gameModuleId) {

        static GameResponse from(GameRow row) {
            return new GameResponse(row.id(), row.name(), row.gameModuleId());
        }
    }
}
