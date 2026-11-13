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

    @GetMapping("/players/{playerId}")
    @Operation(
            summary = "Statistics of a Player",
            description = "Like a Game's statistics, worked out from the statistics' own copy of the Plays. A"
                    + " Cooperative Play is won or lost by the whole group, so it is counted apart from the Plays"
                    + " against others, as are Solo Plays.",
            operationId = "getPlayerStats")
    PlayerSummaryResponse player(@PathVariable UUID playerId) {
        Player player = players.find(playerId).orElseThrow(() -> Problem.notFound("players.notFound"));
        StatsRepository.PlayerTotals totals = repository.totalsOf(playerId);
        List<StatsRepository.GameTally> tallies = repository.gameTalliesOf(playerId);
        Map<UUID, Game> gamesById =
                games.find(tallies.stream().map(StatsRepository.GameTally::gameId).toList());
        // The most played first; of two played as often, the one played last.
        Comparator<StatsRepository.GameTally> mostPlayed = Comparator.comparing(StatsRepository.GameTally::lastPlayedOn)
                .reversed();
        List<PlayerGameStatsResponse> perGame = tallies.stream()
                .filter(tally -> tally.withOthers().plays() > 0 && gamesById.containsKey(tally.gameId()))
                .sorted(Comparator.comparingInt((StatsRepository.GameTally tally) -> tally.withOthers().plays())
                        .reversed()
                        .thenComparing(mostPlayed))
                .map(tally -> {
                    PlayerTally withOthers = tally.withOthers();
                    return new PlayerGameStatsResponse(
                            tally.gameId(),
                            withOthers.plays(),
                            withOthers.wins(),
                            best(gamesById.get(tally.gameId()), withOthers),
                            withOthers.averageScore(),
                            withOthers.averagePlacing());
                })
                .toList();
        List<PlayerSoloStatsResponse> perGameSolo = tallies.stream()
                .filter(tally -> tally.solo().plays() > 0 && gamesById.containsKey(tally.gameId()))
                .sorted(Comparator.comparingInt((StatsRepository.GameTally tally) -> tally.solo().plays())
                        .reversed()
                        .thenComparing(mostPlayed))
                .map(tally -> {
                    PlayerTally solo = tally.solo();
                    return new PlayerSoloStatsResponse(
                            tally.gameId(),
                            solo.plays(),
                            solo.wins(),
                            solo.playsWonOrLost(),
                            best(gamesById.get(tally.gameId()), solo),
                            solo.averageScore());
                })
                .toList();

        return new PlayerSummaryResponse(
                playerId,
                player.name(),
                totals.plays(),
                totals.lastPlayedOn(),
                totals.playsAgainstOthers() == 0
                        ? null
                        : new AgainstOthersResponse(
                                totals.playsAgainstOthers(), totals.winsAgainstOthers(), totals.averagePlacing()),
                totals.playsTogether() == 0
                        ? null
                        : new TogetherResponse(totals.playsTogether(), totals.winsTogether()),
                totals.soloPlays(),
                perGame,
                perGameSolo);
    }

    /** The best total: the highest, or the lowest in a Game where the lowest score wins. */
    private static Integer best(Game game, PlayerTally tally) {
        return game.defaults().lowestScoreWins() ? tally.lowestScore() : tally.highestScore();
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
            name = "PlayerSummary",
            description = "How a Player did in all the Games they played",
            requiredProperties = {"playerId", "name", "plays", "soloPlays", "games", "solo"})
    record PlayerSummaryResponse(
            UUID playerId,
            String name,
            @Schema(description = "Every Play the Player took part in") int plays,
            @Schema(description = "The day of the latest Play. Absent when there is none.") LocalDate lastPlayedOn,
            @Schema(description = "The Plays against others. Absent when there are none.")
                    AgainstOthersResponse againstOthers,
            @Schema(description = "The Cooperative Plays. Absent when there are none.") TogetherResponse together,
            int soloPlays,
            @Schema(description = "One for each Game the Player played with others, the most played first")
                    List<PlayerGameStatsResponse> games,
            @Schema(description = "One for each Game the Player played on their own, the most played first")
                    List<PlayerSoloStatsResponse> solo) {}

    @Schema(name = "AgainstOthers", requiredProperties = {"plays", "wins"})
    record AgainstOthersResponse(
            int plays,
            @Schema(description = "Shared wins included") int wins,
            @Schema(description = "Of the Plays recorded with scores. 1 is first.") Double averagePlacing) {}

    @Schema(
            name = "Together",
            description = "Cooperative Plays, which the whole group wins or loses",
            requiredProperties = {"plays", "wins"})
    record TogetherResponse(int plays, int wins) {}

    @Schema(
            name = "PlayerGameStats",
            description = "How a Player did in one Game, in the Plays with others. Cooperative Plays count in"
                    + " plays and wins; the scores and Placings come from the Plays against others only.",
            requiredProperties = {"gameId", "plays", "wins"})
    record PlayerGameStatsResponse(
            UUID gameId,
            int plays,
            int wins,
            @Schema(description = "The best total: the highest, or the lowest when the lowest score wins. Absent when"
                            + " no score was recorded.")
                    Integer bestScore,
            Double averageScore,
            @Schema(description = "1 is first") Double averagePlacing) {}

    @Schema(
            name = "PlayerSoloStats",
            description = "How a Player did in the Solo Plays of one Game",
            requiredProperties = {"gameId", "plays", "wins", "playsAgainstOpponent"})
    record PlayerSoloStatsResponse(
            UUID gameId,
            int plays,
            @Schema(description = "The Solo Plays won against the Game's automated opponent") int wins,
            @Schema(description = "The Solo Plays against an automated opponent, which are won or lost")
                    int playsAgainstOpponent,
            Integer bestScore,
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
