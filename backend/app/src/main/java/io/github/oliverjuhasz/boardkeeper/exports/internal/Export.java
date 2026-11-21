package io.github.oliverjuhasz.boardkeeper.exports.internal;

import io.github.oliverjuhasz.boardkeeper.gameapi.RecordingMode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * An Export: an Installation's Games, Players and Plays as one JSON file, to use them elsewhere or
 * to bring them into another Installation by an Import (ADR 0020). It holds nothing about Accounts.
 *
 * <p>This is version {@value #FORMAT} of the format, and every version stays importable (ADR 0022).
 * A change that an older Board Keeper could not read raises {@link #FORMAT}, and Board Keeper keeps a
 * way to read the older versions; a sample file of each is kept with the tests. Adding a field that
 * may be absent changes nothing for an older Board Keeper, which ignores fields it doesn't know, so
 * it doesn't raise the version.
 *
 * <p>The ids are those of the Installation that wrote the file. Within the file, a Play names its Game
 * and its Players by them. The keys in a Play's Setup, scores and Player Setup are its Game Module's,
 * which never changes them once released.
 */
@Schema(
        name = "Export",
        description = "An Installation's Games, Players and Plays. It holds nothing about Accounts, so it is no"
                + " backup of the Installation. Every version of the format stays importable.",
        requiredProperties = {"format", "exportedAt", "writtenBy", "gameModules", "games", "players", "plays"})
record Export(
        @Schema(description = "The version of the format, raised only by a change an older Board Keeper can't read")
                // An Integer rather than an int, so that a file without it reaches the Import, which
                // says it isn't an Export, instead of failing to be read at all.
                Integer format,
        Instant exportedAt,
        @Schema(description = "Which Board Keeper wrote the file") WrittenBy writtenBy,
        @Schema(description = "The key of each Game Module the Games and Plays name, for people. A Game Module that"
                        + " the writing Installation didn't have is missing.")
                List<ExportedGameModule> gameModules,
        @Schema(description = "Ordered by name") List<ExportedGame> games,
        @Schema(description = "Ordered by name") List<ExportedPlayer> players,
        @Schema(description = "From the oldest day on, and within a day in the order they were recorded")
                List<ExportedPlay> plays) {

    /** The version of the format that this Board Keeper writes. */
    static final int FORMAT = 1;

    @Schema(name = "ExportWrittenBy")
    record WrittenBy(
            @Schema(description = "The release, such as 1.2.3. Absent for a Snapshot.") String version,
            @Schema(description = "The commit it was built from. Absent when it was built without git.") String commit) {}

    @Schema(
            name = "ExportedGameModule",
            requiredProperties = {"id", "key"})
    record ExportedGameModule(UUID id, String key) {}

    @Schema(
            name = "ExportedGame",
            requiredProperties = {"id", "name", "gameModuleId"})
    record ExportedGame(
            UUID id,
            String name,
            @Schema(description = "The Game Module that scores the Game. A Play may have been scored by another one.")
                    UUID gameModuleId,
            @Schema(description = "How its Plays are recorded unless the recorder chooses otherwise. Only for a Game"
                            + " scored by the Generic Module: any other module brings its own.")
                    ExportedRecordingDefaults recordingDefaults) {}

    @Schema(
            name = "ExportedRecordingDefaults",
            requiredProperties = {"cooperative", "recordingMode", "lowestScoreWins"})
    record ExportedRecordingDefaults(boolean cooperative, RecordingMode recordingMode, boolean lowestScoreWins) {}

    @Schema(
            name = "ExportedPlayer",
            requiredProperties = {"id", "name"})
    record ExportedPlayer(UUID id, String name) {}

    @Schema(
            name = "ExportedPlay",
            requiredProperties = {
                "id",
                "gameId",
                "gameModuleId",
                "playedOn",
                "recordingMode",
                "setup",
                "cooperative",
                "results",
                "recordedBy",
                "recordedAt"
            })
    record ExportedPlay(
            UUID id,
            @Schema(description = "One of the file's Games") UUID gameId,
            @Schema(description = "The Game Module that scored the Play. The keys in \"setup\", \"scores\" and"
                            + " \"victoryType\" are its.")
                    UUID gameModuleId,
            LocalDate playedOn,
            String location,
            String notes,
            RecordingMode recordingMode,
            @Schema(description = "The Setup: the value chosen for each Variant, and whether each Expansion was played"
                            + " with, by their keys")
                    Map<String, String> setup,
            @Schema(description = "How it was won, by the key of one of its Game Module's Victory Types. Absent for a"
                            + " Game that is won in one way only.")
                    String victoryType,
            @Schema(description = "Whether the Players won or lost together") boolean cooperative,
            @Schema(description = "For a Cooperative Play: how it ended for the group") ExportedGroupResult group,
            @Schema(description = "One per Player, in the order they were entered") List<ExportedResult> results,
            @Schema(description = "The file's Player who recorded it") UUID recordedBy,
            Instant recordedAt) {}

    @Schema(
            name = "ExportedResult",
            description = "What one Player got in a Play: what was entered for them, and what their Game Module"
                    + " worked out from it",
            requiredProperties = {"playerId", "scores", "setup", "markedWinner"})
    record ExportedResult(
            @Schema(description = "One of the file's Players") UUID playerId,
            @Schema(description = "As entered: the value for each Score Category") Map<String, Integer> scores,
            @Schema(description = "As entered: the Player Setup, the option chosen for each choice") Map<String, String> setup,
            @Schema(description = "As entered: whether the recorder marked the Player as a winner") boolean markedWinner,
            @Schema(description = "Absent when no scores were recorded for the Player") Integer total,
            @Schema(description = "Absent in a Cooperative Play and a Solo Play, and when only the winner was recorded")
                    Integer placing,
            @Schema(description = "Absent in a Solo Play recorded for score only, which nobody wins or loses")
                    Boolean won) {}

    @Schema(
            name = "ExportedGroupResult",
            requiredProperties = {"won", "scores"})
    record ExportedGroupResult(
            boolean won,
            @Schema(description = "As entered: the group's value for each Score Category") Map<String, Integer> scores,
            @Schema(description = "Absent when no score was recorded") Integer total) {}
}
