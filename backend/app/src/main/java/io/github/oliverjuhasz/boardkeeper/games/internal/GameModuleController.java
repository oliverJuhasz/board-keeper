package io.github.oliverjuhasz.boardkeeper.games.internal;

import io.github.oliverjuhasz.boardkeeper.gameapi.GameModule;
import io.github.oliverjuhasz.boardkeeper.games.GameModules;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/game-modules")
@Tag(name = "Game Modules")
class GameModuleController {

    private final GameModules gameModules;

    GameModuleController(GameModules gameModules) {
        this.gameModules = gameModules;
    }

    @GetMapping
    @Operation(summary = "List the installed Game Modules", operationId = "listGameModules")
    List<GameModuleResponse> list() {
        return gameModules.all().stream().map(GameModuleResponse::from).toList();
    }

    @Schema(name = "GameModule", requiredProperties = {"id", "key"})
    record GameModuleResponse(UUID id, String key) {

        static GameModuleResponse from(GameModule module) {
            return new GameModuleResponse(module.id(), module.key());
        }
    }
}
