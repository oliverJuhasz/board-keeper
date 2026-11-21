package io.github.oliverjuhasz.boardkeeper.exports;

import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.COOPERATIVE;
import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.calicoPad;
import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.idOf;
import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.json;
import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.play;
import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.pointsPlay;
import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.scored;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.oliverjuhasz.boardkeeper.TestBrowser;
import io.github.oliverjuhasz.boardkeeper.TestDatabase;
import io.github.oliverjuhasz.boardkeeper.TestcontainersConfiguration;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.TestAccounts;
import io.github.oliverjuhasz.boardkeeper.gameapi.RecordingMode;
import io.github.oliverjuhasz.boardkeeper.games.Game;
import io.github.oliverjuhasz.boardkeeper.games.Game.RecordingDefaults;
import io.github.oliverjuhasz.boardkeeper.games.Games;
import io.github.oliverjuhasz.boardkeeper.players.Players;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

/** Exports (ADR 0019): the Installation's Games, Players and Plays as one file, for Admins. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ExportApiTests {

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

    private TestBrowser admin;
    private TestBrowser bence;
    private UUID anna;
    private UUID bencesPlayer;
    private UUID dora;
    private Game calico;
    private Game golf;
    private Game hanabi;

    @BeforeEach
    void startWithGamesAndPlayers() {
        database.empty();
        anna = accounts.playerOf(accounts.admin("anna"));
        bencesPlayer = accounts.playerOf(accounts.member("bence"));
        dora = players.create("Dora").id();
        admin = accounts.signedIn(mvc, "anna");
        bence = accounts.signedIn(mvc, "bence");
        games.addGamesOfModules();
        calico = gameNamed("Calico");
        golf = games.create("Golf", new RecordingDefaults(false, RecordingMode.POINTS, true));
        hanabi = games.create("Hanabi", new RecordingDefaults(true, RecordingMode.POINTS, false));
    }

    @Test
    void anAdminSavesTheGamesPlayersAndPlaysAsAFile() {
        String calicoPlay = idOf(bence.post(
                "/api/plays",
                pointsPlay(calico.id(), "2026-03-14", calicoPad(anna, 28, 23, 21), calicoPad(dora, 21, 19, 18))));
        String golfPlay = idOf(admin.post(
                "/api/plays",
                play(golf.id(), "POINTS", "{\"lowestScoreWins\": \"true\"}", "2026-03-01", scored(anna, 70), scored(dora, 74))));

        MvcTestResult export = admin.get("/api/export");

        assertThat(export).hasStatusOk();
        assertThat(export.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"board-keeper-%s.json\"".formatted(LocalDate.now(ZoneOffset.UTC)));
        assertThat(export)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"format": 1,
                         "players": [{"id": "%s", "name": "Dora"}, {"id": "%s", "name": "Player of anna"},
                                     {"id": "%s", "name": "Player of bence"}],
                         "plays": [
                           {"id": "%s", "gameId": "%s", "playedOn": "2026-03-01", "recordingMode": "POINTS",
                            "setup": {"lowestScoreWins": "true"}, "cooperative": false, "recordedBy": "%s",
                            "results": [
                              {"playerId": "%s", "scores": {"total": 70}, "setup": {}, "markedWinner": false,
                               "total": 70, "placing": 1, "won": true},
                              {"playerId": "%s", "scores": {"total": 74}, "total": 74, "placing": 2, "won": false}]},
                           {"id": "%s", "gameId": "%s", "gameModuleId": "%s", "recordedBy": "%s",
                            "results": [
                              {"playerId": "%s", "scores": {"designGoals": 28, "cats": 23, "buttons": 21}, "total": 72},
                              {"playerId": "%s", "total": 58}]}]}
                        """.formatted(
                                dora, anna, bencesPlayer,
                                golfPlay, golf.id(), anna, anna, dora,
                                calicoPlay, calico.id(), calico.gameModuleId(), bencesPlayer, anna, dora));
        JsonNode file = json(export);
        // Every Game of the catalogue is in it, those of the Game Modules too. A Game with a module of
        // its own gets its defaults from the module, so the file has none for it.
        assertThat(gameIn(file, "Golf").toString())
                .isEqualTo("{\"id\":\"%s\",\"name\":\"Golf\",\"gameModuleId\":\"%s\",\"recordingDefaults\":"
                        .formatted(golf.id(), golf.gameModuleId())
                        + "{\"cooperative\":false,\"recordingMode\":\"POINTS\",\"lowestScoreWins\":true}}");
        assertThat(gameIn(file, "Calico").toString())
                .isEqualTo("{\"id\":\"%s\",\"name\":\"Calico\",\"gameModuleId\":\"%s\"}"
                        .formatted(calico.id(), calico.gameModuleId()));
        assertThat(file.get("games")).hasSize(games.all().size());
        assertThat(file.get("gameModules").toString()).contains("\"key\":\"calico\"", "\"key\":\"generic\"");
        assertThat(file.get("exportedAt").asString()).isNotBlank();
        assertThat(file.has("writtenBy")).isTrue();
    }

    @Test
    void aCooperativePlayKeepsHowItEndedForTheGroup() {
        admin.post("/api/plays", play(hanabi.id(), "POINTS", COOPERATIVE, "2026-04-02", groupPlayer(anna), groupPlayer(dora))
                .replace("\"players\"", "\"group\": {\"won\": true, \"scores\": {\"total\": 23}}, \"players\""));

        assertThat(admin.get("/api/export"))
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"plays": [{"cooperative": true, "group": {"won": true, "scores": {"total": 23}, "total": 23},
                                    "results": [{"playerId": "%s", "won": true}, {"playerId": "%s", "won": true}]}]}
                        """.formatted(anna, dora));
    }

    @Test
    void anExportHoldsNothingAboutAccounts() {
        JsonNode file = json(admin.get("/api/export"));

        assertThat(fieldsOf(file))
                .containsExactlyInAnyOrder("format", "exportedAt", "writtenBy", "gameModules", "games", "players", "plays");
        for (JsonNode player : file.get("players")) {
            assertThat(fieldsOf(player)).containsExactlyInAnyOrder("id", "name");
        }
        assertThat(file.toString()).doesNotContain("\"anna\"", "\"bence\"", "Admin", "Member");
    }

    @Test
    void onlyAnAdminMayExport() {
        assertThat(bence.get("/api/export")).hasStatus(403).bodyJson().isLenientlyEqualTo("{\"code\": \"access.denied\"}");
    }

    private Game gameNamed(String name) {
        return games.all().stream().filter(game -> game.name().equals(name)).findFirst().orElseThrow();
    }

    private static JsonNode gameIn(JsonNode file, String name) {
        for (JsonNode game : file.get("games")) {
            if (game.get("name").asString().equals(name)) {
                return game;
            }
        }
        throw new AssertionError("The file has no Game named " + name);
    }

    private static String groupPlayer(UUID player) {
        return "{\"playerId\": \"%s\", \"winner\": false}".formatted(player);
    }

    private static List<String> fieldsOf(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.propertyNames().forEach(names::add);
        return names;
    }
}
