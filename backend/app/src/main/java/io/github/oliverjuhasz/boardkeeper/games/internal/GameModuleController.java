package io.github.oliverjuhasz.boardkeeper.games.internal;

import io.github.oliverjuhasz.boardkeeper.gameapi.GameModule;
import io.github.oliverjuhasz.boardkeeper.gameapi.ScoreCategory;
import io.github.oliverjuhasz.boardkeeper.gameapi.ScoreSheet;
import io.github.oliverjuhasz.boardkeeper.gameapi.Variant;
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

    @Schema(
            name = "GameModule",
            requiredProperties = {"id", "key", "scoreSheet"})
    record GameModuleResponse(UUID id, String key, ScoreSheetResponse scoreSheet) {

        static GameModuleResponse from(GameModule module) {
            return new GameModuleResponse(module.id(), module.key(), ScoreSheetResponse.from(module.scoreSheet()));
        }
    }

    @Schema(
            name = "ScoreSheet",
            description = "What is entered for a Play of the module's Game. It holds keys; what people read is in the"
                    + " module's texts: \"<module>.variant.<key>\", \"<module>.category.<key>\" and \"<module>.tie\".",
            requiredProperties = {"minPlayers", "variants", "categories", "recorderDecidesTies"})
    record ScoreSheetResponse(
            int minPlayers,
            @Schema(description = "Absent when there is no limit") Integer maxPlayers,
            List<VariantResponse> variants,
            @Schema(description = "The rows of the score pad, in its order") List<ScoreCategoryResponse> categories,
            @Schema(description = "Whether the recorder is asked who won a tie for first place that the scores"
                            + " cannot decide")
                    boolean recorderDecidesTies) {

        static ScoreSheetResponse from(ScoreSheet sheet) {
            return new ScoreSheetResponse(
                    sheet.minPlayers(),
                    sheet.maxPlayers(),
                    sheet.variants().stream().map(VariantResponse::from).toList(),
                    sheet.categories().stream().map(ScoreCategoryResponse::from).toList(),
                    sheet.recorderDecidesTies());
        }
    }

    enum VariantKind {
        ON_OFF,
        CHOICE
    }

    @Schema(
            name = "Variant",
            requiredProperties = {"key", "kind", "defaultValue"})
    record VariantResponse(
            String key,
            VariantKind kind,
            @Schema(description = "For a CHOICE: the keys of the options, in the order to offer them") List<String> options,
            @Schema(description = "What a Setup holds when nothing was chosen. For ON_OFF, \"true\" or \"false\".")
                    String defaultValue) {

        static VariantResponse from(Variant variant) {
            // The switch has no "default": Variant is sealed, so the compiler knows every kind.
            return switch (variant) {
                case Variant.OnOff onOff -> new VariantResponse(onOff.key(), VariantKind.ON_OFF, null, onOff.defaultValue());
                case Variant.Choice choice ->
                    new VariantResponse(choice.key(), VariantKind.CHOICE, choice.options(), choice.defaultValue());
            };
        }
    }

    @Schema(
            name = "ScoreCategory",
            requiredProperties = {"key", "countsTowardTotal", "step"})
    record ScoreCategoryResponse(
            String key,
            boolean countsTowardTotal,
            @Schema(description = "The lowest value allowed. Absent when there is none.") Integer min,
            @Schema(description = "The highest value allowed. Absent when there is none.") Integer max,
            @Schema(description = "Every value is a multiple of this") int step,
            @Schema(description = "The row is on the pad only when the Setup has this value for this Variant. Absent"
                            + " for a row that is always on it.")
                    OnlyWhenResponse onlyWhen) {

        static ScoreCategoryResponse from(ScoreCategory category) {
            return new ScoreCategoryResponse(
                    category.key(),
                    category.countsTowardTotal(),
                    category.min(),
                    category.max(),
                    category.step(),
                    category.onlyWhen() == null
                            ? null
                            : new OnlyWhenResponse(category.onlyWhen().variant(), category.onlyWhen().value()));
        }
    }

    @Schema(
            name = "OnlyWhen",
            requiredProperties = {"variant", "value"})
    record OnlyWhenResponse(String variant, String value) {}
}
