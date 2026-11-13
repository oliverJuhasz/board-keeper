package io.github.oliverjuhasz.boardkeeper.stats;

import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.COOPERATIVE;
import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.calicoPad;
import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.idOf;
import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.notScored;
import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.play;
import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.pointsPlay;
import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.scored;
import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.winner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.oliverjuhasz.boardkeeper.TestBrowser;
import io.github.oliverjuhasz.boardkeeper.TestDatabase;
import io.github.oliverjuhasz.boardkeeper.TestcontainersConfiguration;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.TestAccounts;
import io.github.oliverjuhasz.boardkeeper.gameapi.RecordingMode;
import io.github.oliverjuhasz.boardkeeper.games.Game.RecordingDefaults;
import io.github.oliverjuhasz.boardkeeper.games.Games;
import io.github.oliverjuhasz.boardkeeper.players.Players;
import java.time.Duration;
import java.util.UUID;
import org.awaitility.core.ThrowingRunnable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * The statistics of a Game and of a Player. They are brought up to date by event listeners that run on threads
 * of their own after a Play has been saved, so every test waits for its numbers to arrive.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class StatsApiTests {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private TestDatabase database;

    @Autowired
    private TestAccounts accounts;

    @Autowired
    private Games games;

    @Autowired
    private Players players;

    private TestBrowser member;
    private UUID anna;
    private UUID bence;
    private UUID dora;

    @BeforeEach
    void startWithThreePlayers() {
        database.empty();
        accounts.member("recorder");
        member = accounts.signedIn(mvc, "recorder");
        anna = players.create("Anna").id();
        bence = players.create("Bence").id();
        dora = players.create("Dora").id();
    }

    @Test
    void countsEachPlayersPlaysWinsScoresAndPlacings() {
        UUID azul = generic("Azul", false);
        member.post("/api/plays", pointsPlay(azul, "2026-03-01", scored(anna, 50), scored(dora, 40)));
        member.post("/api/plays", pointsPlay(azul, "2026-03-08", scored(anna, 30), scored(dora, 45), scored(bence, 20)));
        // Only the winner was recorded: it counts in plays and wins, not in scores and Placings.
        member.post("/api/plays", play(azul, "WINNER_ONLY", "{}", "2026-03-15", winner(dora), notScored(anna)));

        eventually(() -> assertThat(member.get(statsOf(azul)))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"plays": 3, "lastPlayedOn": "2026-03-15",
                         "players": [
                           {"playerId": "%s", "name": "Dora", "plays": 3, "wins": 2,
                            "bestScore": 45, "averageScore": 42.5, "averagePlacing": 1.5},
                           {"playerId": "%s", "name": "Anna", "plays": 3, "wins": 1,
                            "bestScore": 50, "averageScore": 40.0, "averagePlacing": 1.5},
                           {"playerId": "%s", "name": "Bence", "plays": 1, "wins": 0,
                            "bestScore": 20, "averageScore": 20.0, "averagePlacing": 3.0}],
                         "solo": []}
                        """.formatted(dora, anna, bence)));
        // The most wins first.
        assertThat(member.get(statsOf(azul)))
                .bodyJson()
                .extractingPath("$.players[*].name")
                .asArray()
                .containsExactly("Dora", "Anna", "Bence");
    }

    @Test
    void theBestScoreIsTheLowestWhenTheLowestScoreWins() {
        UUID golf = generic("Golf", true);
        member.post("/api/plays", pointsPlay(golf, "2026-03-01", scored(anna, 34), scored(dora, 38)));
        member.post("/api/plays", pointsPlay(golf, "2026-03-08", scored(anna, 41), scored(dora, 36)));

        eventually(() -> assertThat(member.get(statsOf(golf)))
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"plays": 2, "players": [{"name": "Anna", "bestScore": 34}, {"name": "Dora", "bestScore": 36}]}
                        """));
    }

    @Test
    void theAverageOfEachScoreCategoryComesFromTheScorePad() {
        UUID calico = gameOfModule("calico");
        member.post("/api/plays", pointsPlay(calico, "2026-03-01", calicoPad(anna, 28, 23, 21), calicoPad(dora, 21, 19, 18)));

        eventually(() -> assertThat(member.get(statsOf(calico)))
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"plays": 1, "categoryAverages": {"designGoals": 24.5, "cats": 21.0, "buttons": 19.5}}
                        """));
    }

    @Test
    void soloPlaysHaveASectionOfTheirOwn() {
        UUID wyrmspan = gameOfModule("wyrmspan");
        member.post("/api/plays", wyrmspanSolo(wyrmspan, 85));
        member.post("/api/plays", wyrmspanSolo(wyrmspan, 99));
        member.post("/api/plays", play(wyrmspan, "WINNER_ONLY", "{}", "2026-03-15", winner(anna), notScored(dora)));

        eventually(() -> assertThat(member.get(statsOf(wyrmspan)))
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"plays": 3,
                         "players": [{"name": "Anna", "plays": 1, "wins": 1}, {"name": "Dora", "plays": 1, "wins": 0}],
                         "solo": [{"playerId": "%s", "name": "Anna", "plays": 2, "wins": 1, "playsAgainstOpponent": 2,
                                   "bestScore": 93, "averageScore": 93.0}],
                         "categoryAverages": {}}
                        """.formatted(anna)));
    }

    @Test
    void aSoloPlayForAScoreOnlyIsNeitherWonNorLost() {
        UUID calico = gameOfModule("calico");
        member.post("/api/plays", pointsPlay(calico, "2026-03-01", calicoPad(anna, 28, 23, 21)));

        eventually(() -> assertThat(member.get(statsOf(calico)))
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"players": [], "solo": [{"name": "Anna", "plays": 1, "wins": 0, "playsAgainstOpponent": 0,
                                                  "bestScore": 72}]}
                        """));
    }

    @Test
    void cooperativePlaysAreCountedForTheGroup() {
        UUID pandemic = games.create("Pandemic", new RecordingDefaults(true, RecordingMode.POINTS, false)).id();
        String cooperative = """
                {"gameId": "%s", "playedOn": "2026-03-14", "recordingMode": "POINTS", "setup": %s,
                 "players": [%s, %s], "group": {"won": %s, "scores": {"total": %d}}}
                """;
        member.post("/api/plays", cooperative.formatted(pandemic, COOPERATIVE, notScored(anna), notScored(dora), true, 17));
        member.post("/api/plays", cooperative.formatted(pandemic, COOPERATIVE, notScored(anna), notScored(dora), false, 10));

        eventually(() -> assertThat(member.get(statsOf(pandemic)))
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"plays": 2, "players": [], "solo": [],
                         "cooperative": {"plays": 2, "wins": 1, "bestScore": 17, "averageScore": 13.5}}
                        """));
    }

    @Test
    void changingAndDeletingAPlayReachTheStatistics() {
        UUID azul = generic("Azul", false);
        String id = idOf(member.post("/api/plays", pointsPlay(azul, "2026-03-01", scored(anna, 50), scored(dora, 40))));
        eventually(() -> assertThat(member.get(statsOf(azul))).bodyJson().extractingPath("$.plays").isEqualTo(1));

        member.put("/api/plays/" + id, pointsPlay(azul, "2026-03-01", scored(anna, 50), scored(dora, 60)));
        eventually(() -> assertThat(member.get(statsOf(azul)))
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"players": [{"name": "Dora", "wins": 1, "bestScore": 60}, {"name": "Anna", "wins": 0}]}
                        """));

        member.delete("/api/plays/" + id);
        eventually(() -> assertThat(member.get(statsOf(azul)))
                .bodyJson()
                .isLenientlyEqualTo("{\"plays\": 0, \"players\": [], \"categoryAverages\": {}}"));
        assertThat(member.get(statsOf(azul))).bodyJson().doesNotHavePath("$.lastPlayedOn");
    }

    @Test
    void anUnknownGameIsNotFound() {
        assertThat(member.get(statsOf(UUID.randomUUID())))
                .hasStatus(404)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"games.notFound\"}");
    }

    @Test
    void aPlayersStatisticsCoverEveryGameTheyPlayed() {
        UUID azul = generic("Azul", false);
        UUID pandemic = games.create("Pandemic", new RecordingDefaults(true, RecordingMode.POINTS, false)).id();
        UUID calico = gameOfModule("calico");
        UUID wyrmspan = gameOfModule("wyrmspan");
        member.post("/api/plays", pointsPlay(azul, "2026-03-01", scored(anna, 50), scored(dora, 40)));
        member.post("/api/plays", pointsPlay(azul, "2026-03-02", scored(anna, 30), scored(dora, 45)));
        member.post("/api/plays", play(wyrmspan, "WINNER_ONLY", "{}", "2026-03-03", winner(anna), notScored(bence)));
        member.post("/api/plays", """
                {"gameId": "%s", "playedOn": "2026-03-04", "recordingMode": "WINNER_ONLY", "setup": %s,
                 "players": [%s, %s], "group": {"won": true, "scores": {}}}
                """.formatted(pandemic, COOPERATIVE, notScored(anna), notScored(dora)));
        member.post("/api/plays", pointsPlay(calico, "2026-03-05", calicoPad(anna, 28, 23, 21)));

        eventually(() -> assertThat(member.get("/api/stats/players/" + anna))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"playerId": "%s", "name": "Anna", "plays": 5, "lastPlayedOn": "2026-03-05",
                         "againstOthers": {"plays": 3, "wins": 2, "averagePlacing": 1.5},
                         "together": {"plays": 1, "wins": 1},
                         "soloPlays": 1,
                         "games": [
                           {"gameId": "%s", "plays": 2, "wins": 1, "bestScore": 50, "averageScore": 40.0,
                            "averagePlacing": 1.5},
                           {"gameId": "%s", "plays": 1, "wins": 1},
                           {"gameId": "%s", "plays": 1, "wins": 1}],
                         "solo": [{"gameId": "%s", "plays": 1, "wins": 0, "playsAgainstOpponent": 0, "bestScore": 72,
                                   "averageScore": 72.0}]}
                        """.formatted(anna, azul, pandemic, wyrmspan, calico)));
        // The Games played as often come in the order they were last played, the latest first.
        assertThat(member.get("/api/stats/players/" + anna))
                .bodyJson()
                .extractingPath("$.games[*].gameId")
                .asArray()
                .containsExactly(azul.toString(), pandemic.toString(), wyrmspan.toString());
        assertThat(member.get("/api/stats/players/" + anna)).bodyJson().doesNotHavePath("$.games[1].bestScore");
    }

    @Test
    void aPlayerWhoHasNotPlayedHasNoStatisticsYet() {
        assertThat(member.get("/api/stats/players/" + bence))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"name\": \"Bence\", \"plays\": 0, \"soloPlays\": 0, \"games\": [], \"solo\": []}");
        assertThat(member.get("/api/stats/players/" + bence)).bodyJson().doesNotHavePath("$.againstOthers");
    }

    @Test
    void anUnknownPlayerIsNotFound() {
        assertThat(member.get("/api/stats/players/" + UUID.randomUUID()))
                .hasStatus(404)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"players.notFound\"}");
    }

    @Test
    void needsASignedInAccount() {
        UUID azul = generic("Azul", false);

        assertThat(TestBrowser.open(mvc).get(statsOf(azul))).hasStatus(401);
    }

    private static String statsOf(UUID game) {
        return "/api/stats/games/" + game;
    }

    /** Retries an assertion until it holds, for at most ten seconds. */
    private static void eventually(ThrowingRunnable assertion) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(assertion);
    }

    private UUID generic(String name, boolean lowestScoreWins) {
        return games.create(name, new RecordingDefaults(false, RecordingMode.POINTS, lowestScoreWins)).id();
    }

    /** A Solo Play of Anna against the Automa, in which Anna scores 93. */
    private String wyrmspanSolo(UUID wyrmspan, int automa) {
        return """
                {"gameId": "%s", "playedOn": "2026-03-14", "recordingMode": "POINTS",
                 "setup": {"automaLevel": "2", "ravelMode": "false"},
                 "players": [{"playerId": "%s", "winner": false, "scores": {
                   "dragonGuild": 12, "printedDragons": 40, "endGameAbilities": 6, "eggs": 9, "cachedResources": 7,
                   "tuckedCards": 5, "publicObjectives": 11, "coinsAndItems": 3, "automa": %d}}]}
                """.formatted(wyrmspan, anna, automa);
    }

    /** The Game that appears in the catalogue for the Game Module with this key. */
    private UUID gameOfModule(String key) {
        games.addGamesOfModules();
        return games.all().stream()
                .filter(game -> games.moduleKeyOf(game).filter(key::equals).isPresent())
                .findFirst()
                .orElseThrow()
                .id();
    }
}
