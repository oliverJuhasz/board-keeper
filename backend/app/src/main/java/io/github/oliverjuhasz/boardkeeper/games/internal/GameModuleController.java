package io.github.oliverjuhasz.boardkeeper.games.internal;

import io.github.oliverjuhasz.boardkeeper.gameapi.GameModule;
import io.github.oliverjuhasz.boardkeeper.gameapi.OnlyWhen;
import io.github.oliverjuhasz.boardkeeper.gameapi.ScoreCategory;
import io.github.oliverjuhasz.boardkeeper.gameapi.ScorePad;
import io.github.oliverjuhasz.boardkeeper.gameapi.Solo;
import io.github.oliverjuhasz.boardkeeper.gameapi.Variant;
import io.github.oliverjuhasz.boardkeeper.gameapi.VictoryType;
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
            requiredProperties = {"id", "key", "scorePad"})
    record GameModuleResponse(UUID id, String key, ScorePadResponse scorePad) {

        static GameModuleResponse from(GameModule module) {
            return new GameModuleResponse(module.id(), module.key(), ScorePadResponse.from(module.scorePad()));
        }
    }

    @Schema(
            name = "ScorePad",
            description = "What is entered for a Play of the module's Game. It holds keys; what people read is in the"
                    + " module's texts: \"<module>.expansion.<key>\", \"<module>.variant.<key>\","
                    + " \"<module>.category.<key>\", \"<module>.victoryType.<key>\", \"<module>.playerSetup.<key>\","
                    + " \"<module>.playerSetup.<key>.<option>\" and \"<module>.tie\".",
            requiredProperties = {
                "minPlayers",
                "expansions",
                "variants",
                "categories",
                "recorderDecidesTies",
                "victoryTypes",
                "playerSetup"
            })
    record ScorePadResponse(
            int minPlayers,
            @Schema(description = "Absent when there is no limit") Integer maxPlayers,
            @Schema(description = "The Expansions a Play can be played with, in the order to offer them")
                    List<ExpansionResponse> expansions,
            List<VariantResponse> variants,
            @Schema(description = "The rows of the score pad, in its order") List<ScoreCategoryResponse> categories,
            @Schema(description = "Whether the recorder is asked who won a tie for first place that the scores"
                            + " cannot decide")
                    boolean recorderDecidesTies,
            @Schema(description = "How a Solo Play is recorded. Absent for a Game that is not played alone.")
                    SoloResponse solo,
            @Schema(description = "The ways a Play can be won, the usual one first. Empty for a Game that is won in"
                            + " one way only.")
                    List<VictoryTypeResponse> victoryTypes,
            @Schema(description = "The choices made for each Player, such as their Wonder, in the order to offer them."
                            + " Empty for a Game without Player Setup.")
                    List<PlayerSetupChoiceResponse> playerSetup) {

        static ScorePadResponse from(ScorePad pad) {
            return new ScorePadResponse(
                    pad.minPlayers(),
                    pad.maxPlayers(),
                    pad.expansions().stream().map(expansion -> new ExpansionResponse(expansion.key())).toList(),
                    pad.variants().stream().map(VariantResponse::from).toList(),
                    pad.categories().stream().map(ScoreCategoryResponse::from).toList(),
                    pad.recorderDecidesTies(),
                    SoloResponse.from(pad.solo()),
                    pad.victoryTypes().stream().map(VictoryTypeResponse::from).toList(),
                    pad.playerSetup().stream()
                            .map(choice -> new PlayerSetupChoiceResponse(choice.key(), choice.options(), choice.oncePerPlay()))
                            .toList());
        }
    }

    @Schema(
            name = "PlayerSetupChoice",
            description = "Something chosen for each Player, such as their Wonder. It may be left out for a Player.",
            requiredProperties = {"key", "options", "oncePerPlay"})
    record PlayerSetupChoiceResponse(
            String key,
            @Schema(description = "The keys of the options, in the order to offer them") List<String> options,
            @Schema(description = "Whether no two Players of a Play can have the same option") boolean oncePerPlay) {}

    @Schema(
            name = "Expansion",
            description = "In a Play's Setup, \"true\" when it was played with, and \"false\" or absent when not",
            requiredProperties = {"key"})
    record ExpansionResponse(String key) {}

    @Schema(
            name = "VictoryType",
            requiredProperties = {"key", "onPoints"})
    record VictoryTypeResponse(
            String key,
            @Schema(description = "Whether the score pad decides the winner. If not, the Play ended outright: it is"
                            + " recorded with WINNER_ONLY and has one winner.")
                    boolean onPoints,
            @Schema(description = "This way of winning is possible only when the Setup has this value. Absent when"
                            + " it always is.")
                    OnlyWhenResponse onlyWhen) {

        static VictoryTypeResponse from(VictoryType victoryType) {
            return new VictoryTypeResponse(
                    victoryType.key(), victoryType.onPoints(), OnlyWhenResponse.from(victoryType.onlyWhen()));
        }
    }

    enum SoloKind {
        /** Played for a score: no winner and no Placing. */
        FOR_SCORE_ONLY,
        /** Played against an automated opponent, whose score is a row of the pad. */
        AGAINST_OPPONENT
    }

    @Schema(
            name = "Solo",
            requiredProperties = {"kind"})
    record SoloResponse(
            SoloKind kind,
            @Schema(description = "For AGAINST_OPPONENT: the key of the Score Category with the opponent's score")
                    String opponentCategory,
            @Schema(description = "For AGAINST_OPPONENT: whether the Player wins when level with the opponent")
                    Boolean tieWins) {

        static SoloResponse from(Solo solo) {
            if (solo == null) {
                return null;
            }
            return switch (solo) {
                case Solo.ForScoreOnly forScoreOnly -> new SoloResponse(SoloKind.FOR_SCORE_ONLY, null, null);
                case Solo.AgainstOpponent against ->
                    new SoloResponse(SoloKind.AGAINST_OPPONENT, against.opponentCategory(), against.tieWins());
            };
        }
    }

    enum VariantKind {
        ON_OFF,
        CHOICE
    }

    @Schema(
            name = "Variant",
            requiredProperties = {"key", "kind", "defaultValue", "soloOnly"})
    record VariantResponse(
            String key,
            VariantKind kind,
            @Schema(description = "For a CHOICE: the keys of the options, in the order to offer them") List<String> options,
            @Schema(description = "What a Setup holds when nothing was chosen. For ON_OFF, \"true\" or \"false\".")
                    String defaultValue,
            @Schema(description = "Whether it is chosen for a Solo Play only") boolean soloOnly) {

        static VariantResponse from(Variant variant) {
            // The switch has no "default": Variant is sealed, so the compiler knows every kind.
            return switch (variant) {
                case Variant.OnOff onOff ->
                    new VariantResponse(onOff.key(), VariantKind.ON_OFF, null, onOff.defaultValue(), onOff.soloOnly());
                case Variant.Choice choice ->
                    new VariantResponse(
                            choice.key(), VariantKind.CHOICE, choice.options(), choice.defaultValue(), choice.soloOnly());
            };
        }
    }

    @Schema(
            name = "ScoreCategory",
            requiredProperties = {"key", "countsTowardTotal", "step", "soloOnly"})
    record ScoreCategoryResponse(
            String key,
            boolean countsTowardTotal,
            @Schema(description = "The lowest value allowed. Absent when there is none.") Integer min,
            @Schema(description = "The highest value allowed. Absent when there is none.") Integer max,
            @Schema(description = "Every value is a multiple of this") int step,
            @Schema(description = "The row is on the pad only when the Setup has this value. Absent for a row that"
                            + " is always on it.")
                    OnlyWhenResponse onlyWhen,
            @Schema(description = "Whether the row is on the pad of a Solo Play only") boolean soloOnly) {

        static ScoreCategoryResponse from(ScoreCategory category) {
            return new ScoreCategoryResponse(
                    category.key(),
                    category.countsTowardTotal(),
                    category.min(),
                    category.max(),
                    category.step(),
                    OnlyWhenResponse.from(category.onlyWhen()),
                    category.soloOnly());
        }
    }

    @Schema(
            name = "OnlyWhen",
            description = "A condition on a Play's Setup",
            requiredProperties = {"key", "value"})
    record OnlyWhenResponse(
            @Schema(description = "The key of a Variant or an Expansion") String key,
            @Schema(description = "The value it has to have. For an Expansion, \"true\" when it is played with.")
                    String value) {

        static OnlyWhenResponse from(OnlyWhen onlyWhen) {
            return onlyWhen == null ? null : new OnlyWhenResponse(onlyWhen.key(), onlyWhen.value());
        }
    }
}
