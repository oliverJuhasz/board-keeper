package io.github.oliverjuhasz.boardkeeper.games.internal;

import io.github.oliverjuhasz.boardkeeper.gameapi.GameModule;
import io.github.oliverjuhasz.boardkeeper.games.GameModules;
import io.github.oliverjuhasz.boardkeeper.language.Language;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/game-modules")
@Tag(name = "Game Modules")
class GameModuleController {

    private final GameModules gameModules;
    private final GameModuleTexts texts;

    GameModuleController(GameModules gameModules, GameModuleTexts texts) {
        this.gameModules = gameModules;
        this.texts = texts;
    }

    @GetMapping
    @Operation(summary = "List the installed Game Modules", operationId = "listGameModules")
    List<GameModuleResponse> list() {
        return gameModules.all().stream().map(GameModuleResponse::from).toList();
    }

    @GetMapping("/texts/{language}")
    @Operation(
            summary = "The texts of every installed Game Module in one language, by name",
            description = "A text that has no translation in the language asked for is given in English.",
            operationId = "getGameModuleTexts")
    Map<String, String> texts(@PathVariable Language language) {
        return texts.in(language);
    }

    @Schema(name = "GameModule", requiredProperties = {"id", "key"})
    record GameModuleResponse(UUID id, String key) {

        static GameModuleResponse from(GameModule module) {
            return new GameModuleResponse(module.id(), module.key());
        }
    }
}
