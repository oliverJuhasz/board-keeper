package io.github.oliverjuhasz.boardkeeper.stats.internal;

import io.github.oliverjuhasz.boardkeeper.games.Game;
import io.github.oliverjuhasz.boardkeeper.games.Games;
import io.github.oliverjuhasz.boardkeeper.players.Player;
import io.github.oliverjuhasz.boardkeeper.players.Players;
import io.github.oliverjuhasz.boardkeeper.problems.Problem;
import io.github.oliverjuhasz.boardkeeper.stats.internal.StatsRepository.GroupTally;
import io.github.oliverjuhasz.boardkeeper.stats.internal.StatsRepository.PlayCount;
import io.github.oliverjuhasz.boardkeeper.stats.internal.StatsRepository.PlayerTally;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@Tag(name = "Statistics")
class StatsController {

    private final StatsRepository repository;
    private final Games games;
    private final Players players;

    StatsController(StatsRepository repository, Games games, Players players) {
        this.repository = repository;
        this.games = games;
        this.players = players;
    }

    @GetMapping("/games/{gameId}")
    @Operation(
            summary = "Statistics of a Game",
            description = "Worked out from the statistics' own copy of the Plays, which catches up with a Play a"
                    + " moment after it is recorded, changed or deleted. Solo Plays have a section of their own"
                    + " and don't count anywhere else. A Play recorded with only the winner counts in plays and"
                    + " wins, and not in scores or Placings.",
            operationId = "getGameStats")
    GameStatsResponse game(@PathVariable UUID gameId) {
        Game game = games.find(gameId).orElseThrow(() -> Problem.notFound("games.notFound"));
        boolean lowestWins = game.defaults().lowestScoreWins();
        PlayCount count = repository.playCount(gameId);
        List<PlayerTally> competitive = repository.playerTallies(gameId, false);
        List<PlayerTally> solo = repository.playerTallies(gameId, true);
        Map<UUID, Player> named = players.find(
                Stream.concat(competitive.stream(), solo.stream())
                        .map(PlayerTally::playerId)
                        .collect(Collectors.toSet()));

        // A Player removed after the copy was last brought up to date has no name: left out.
        List<PlayerStatsResponse> playerStats = competitive.stream()
                .filter(tally -> named.containsKey(tally.playerId()))
                .map(tally -> new PlayerStatsResponse(
                        tally.playerId(),
                        named.get(tally.playerId()).name(),
                        tally.plays(),
                        tally.wins(),
                        lowestWins ? tally.lowestScore() : tally.highestScore(),
                        tally.averageScore(),
                        tally.averagePlacing()))
                // The most wins first. Of two with as many wins, the one who needed fewer Plays.
                .sorted(Comparator.comparingInt(PlayerStatsResponse::wins)
                        .reversed()
                        .thenComparingInt(PlayerStatsResponse::plays)
                        .thenComparing(PlayerStatsResponse::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<SoloStatsResponse> soloStats = solo.stream()
                .filter(tally -> named.containsKey(tally.playerId()))
                .map(tally -> new SoloStatsResponse(
                        tally.playerId(),
                        named.get(tally.playerId()).name(),
                        tally.plays(),
                        tally.wins(),
                        tally.playsWonOrLost(),
                        lowestWins ? tally.lowestScore() : tally.highestScore(),
                        tally.averageScore()))
                .sorted(Comparator.comparingInt(SoloStatsResponse::plays)
                        .reversed()
                        .thenComparing(SoloStatsResponse::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        GroupTally group = repository.groupTally(gameId);

        return new GameStatsResponse(
                gameId,
                game.gameModuleId(),
                count.plays(),
                count.lastPlayedOn(),
                playerStats,
                categoryAverages(repository.competitiveScores(gameId, game.gameModuleId())),
                group == null
                        ? null
                        : new GroupStatsResponse(group.plays(), group.wins(), group.highestScore(), group.averageScore()),
                soloStats);
    }

    /** The average of each Score Category, over the Players that have a score for it. */
    private static Map<String, Double> categoryAverages(List<Map<String, Integer>> scores) {
        Map<String, int[]> sumAndCount = new HashMap<>();
        for (Map<String, Integer> entered : scores) {
            entered.forEach((category, value) -> {
                int[] sum = sumAndCount.computeIfAbsent(category, key -> new int[2]);
                sum[0] += value;
                sum[1]++;
            });
        }
        return sumAndCount.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> (double) entry.getValue()[0] / entry.getValue()[1]));
    }

    @Schema(
            name = "GameStats",
            requiredProperties = {"gameId", "gameModuleId", "plays", "players", "categoryAverages", "solo"})
    record GameStatsResponse(
            UUID gameId,
            @Schema(description = "The Game Module that scores the Game today. The keys in \"categoryAverages\" are"
                            + " its Score Categories.")
                    UUID gameModuleId,
            @Schema(description = "How many times the Game was played, Cooperative and Solo Plays included") int plays,
            @Schema(description = "The day of the latest Play. Absent when there is none.") LocalDate lastPlayedOn,
            @Schema(description = "One for each Player who played the Game against others, the most wins first")
                    List<PlayerStatsResponse> players,
            @Schema(description = "In the Plays in which Players played against each other: the average of each"
                            + " Score Category, by the category's key. Only Plays scored by the Game Module of"
                            + " today count. Averages are not rounded.")
                    Map<String, Double> categoryAverages,
            @Schema(description = "How the group did in the Cooperative Plays. Absent when there are none.")
                    GroupStatsResponse cooperative,
            @Schema(description = "One for each Player who played the Game on their own, the most Plays first")
                    List<SoloStatsResponse> solo) {}

    @Schema(
            name = "PlayerStats",
            description = "How a Player did in the Plays in which they played against others",
            requiredProperties = {"playerId", "name", "plays", "wins"})
    record PlayerStatsResponse(
            UUID playerId,
            String name,
            int plays,
            @Schema(description = "The Plays the Player won, shared wins included") int wins,
            @Schema(description = "The best total: the highest, or the lowest when the lowest score wins. Absent when"
                            + " no score was recorded.")
                    Integer bestScore,
            @Schema(description = "The average total, of the Plays recorded with scores") Double averageScore,
            @Schema(description = "The average Placing, of the Plays recorded with scores. 1 is first.")
                    Double averagePlacing) {}

    @Schema(
            name = "GroupStats",
            description = "How the group did in the Cooperative Plays",
            requiredProperties = {"plays", "wins"})
    record GroupStatsResponse(
            int plays,
            int wins,
            @Schema(description = "The highest score of the group. Absent when no score was recorded.") Integer bestScore,
            Double averageScore) {}

    @Schema(
            name = "SoloStats",
            description = "How a Player did in the Solo Plays",
            requiredProperties = {"playerId", "name", "plays", "wins", "playsAgainstOpponent"})
    record SoloStatsResponse(
            UUID playerId,
            String name,
            int plays,
            @Schema(description = "The Solo Plays won against the Game's automated opponent") int wins,
            @Schema(description = "The Solo Plays against an automated opponent, which are won or lost. The others"
                            + " were played for a score only.")
                    int playsAgainstOpponent,
            @Schema(description = "The best total: the highest, or the lowest when the lowest score wins. Absent when"
                            + " no score was recorded.")
                    Integer bestScore,
            Double averageScore) {}
}
