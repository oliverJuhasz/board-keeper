package io.github.oliverjuhasz.boardkeeper.exports;

import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.calicoPad;
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
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Imports (ADR 0021): bringing the Plays of an Export into this Installation, as they were. Most
 * tests import {@code exports/format-1.json}, a file of the first version of the format, which
 * every later Board Keeper must still import (ADR 0022).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ImportApiTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** The sample file's Players, Games and Plays, by their ids in the file. */
    private static final String FILE_ANNA = "0190a000-0000-7000-8000-00000000b001";

    private static final String FILE_DORA = "0190a000-0000-7000-8000-00000000b002";
    private static final String FILE_ZSOFI = "0190a000-0000-7000-8000-00000000b003";
    private static final String GOLF_PLAY = "0190a000-0000-7000-8000-00000000c001";
    private static final String CALICO_PLAY = "0190a000-0000-7000-8000-00000000c002";
    private static final String REFUSED_CALICO_PLAY = "0190a000-0000-7000-8000-00000000c003";
    private static final String FANCY_PLAY = "0190a000-0000-7000-8000-00000000c004";
    private static final String CATAN_PLAY = "0190a000-0000-7000-8000-00000000c005";
    private static final String HANABI_PLAY = "0190a000-0000-7000-8000-00000000c006";

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
    private UUID dora;
    private Game calico;
    private Game golf;

    @BeforeEach
    void startWithAnInstallationOfItsOwn() {
        database.empty();
        anna = accounts.playerOf(accounts.admin("anna"));
        accounts.member("bence");
        dora = players.create("Dora").id();
        admin = accounts.signedIn(mvc, "anna");
        bence = accounts.signedIn(mvc, "bence");
        games.addGamesOfModules();
        calico = games.all().stream()
                .filter(game -> game.name().equals("Calico"))
                .findFirst()
                .orElseThrow();
        // Added by hand here too, under other capitals.
        golf = games.create("golf", new RecordingDefaults(false, RecordingMode.POINTS, true));
    }

    @Test
    void aReviewSaysWhatTheImportWouldDoAndChangesNothing() {
        assertThat(admin.post("/api/import/review", sample()))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"games": [
                           {"name": "Calico", "match": "CATALOGUE", "gameId": "%s"},
                           {"name": "Catan", "match": "NEW"},
                           {"name": "Fancy Game", "match": "MODULE_MISSING", "gameModuleKey": "fancy-game"},
                           {"name": "Golf", "match": "CATALOGUE", "gameId": "%s"},
                           {"name": "Hanabi", "match": "NEW"}],
                         "players": [
                           {"fileId": "%s", "name": "Anna"},
                           {"fileId": "%s", "name": "Dora", "suggestedPlayerId": "%s"},
                           {"fileId": "%s", "name": "Zsófi"}],
                         "plays": 6, "alreadyHere": 0,
                         "refused": [
                           {"playId": "%s", "playedOn": "2026-02-02", "game": "Calico",
                            "reason": "plays.invalid.scoreNotAllowed"},
                           {"playId": "%s", "playedOn": "2026-02-03", "game": "Fancy Game",
                            "reason": "imports.gameModuleMissing", "gameModuleKey": "fancy-game"}]}
                        """.formatted(
                                calico.id(), golf.id(),
                                FILE_ANNA, FILE_DORA, dora, FILE_ZSOFI,
                                REFUSED_CALICO_PLAY, FANCY_PLAY));
        // Anna here is "Player of anna", so nothing is suggested for the file's Anna.
        assertThat(admin.post("/api/import/review", sample())).bodyJson().doesNotHavePath("$.players[0].suggestedPlayerId");
        assertThat(admin.get("/api/plays")).bodyJson().isLenientlyEqualTo("{\"plays\": []}");
        assertThat(games.findByName("Catan")).isEmpty();
    }

    @Test
    void anImportCopiesThePlaysAsTheyWereWhereTheyWereRecorded() {
        MvcTestResult imported = admin.post("/api/import", request(sample(), chosen(FILE_ANNA, anna), chosen(FILE_DORA, dora), named(FILE_ZSOFI, "Zsófi")));

        assertThat(imported)
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"added": 4, "alreadyHere": 0, "newPlayers": 1, "newGames": 2,
                         "refused": [{"playId": "%s"}, {"playId": "%s"}]}
                        """.formatted(REFUSED_CALICO_PLAY, FANCY_PLAY));
        // Its Results stay as the file has them, though this module would place the lowest score
        // first (ADR 0021). Who recorded it, and when, stay too.
        assertThat(admin.get("/api/plays/" + GOLF_PLAY))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"game": {"id": "%s", "name": "golf"}, "playedOn": "2026-01-10", "location": "Utrecht",
                         "notes": "A close one.", "setup": {"lowestScoreWins": "true"},
                         "results": [{"player": {"id": "%s"}, "total": 70, "placing": 2, "won": false},
                                     {"player": {"id": "%s"}, "total": 74, "placing": 1, "won": true}],
                         "recordedBy": {"id": "%s"}, "recordedAt": "2026-01-10T21:30:00Z"}
                        """.formatted(golf.id(), anna, dora, anna));
        assertThat(admin.get("/api/plays/" + CALICO_PLAY))
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"game": {"id": "%s"}, "gameModuleId": "%s", "recordedBy": {"id": "%s", "name": "Dora"},
                         "results": [{"scores": {"designGoals": 28, "cats": 23, "buttons": 21}, "total": 72}, {"total": 58}]}
                        """.formatted(calico.id(), calico.gameModuleId(), dora));
        assertThat(admin.get("/api/plays/" + CATAN_PLAY))
                .bodyJson()
                .isLenientlyEqualTo("{\"recordingMode\": \"WINNER_ONLY\", \"results\": [{\"won\": true}, {\"won\": false}]}");
        assertThat(admin.get("/api/plays/" + HANABI_PLAY))
                .bodyJson()
                .isLenientlyEqualTo("{\"cooperative\": true, \"group\": {\"won\": true, \"total\": 23}}");
        assertThat(admin.get("/api/plays/" + REFUSED_CALICO_PLAY)).hasStatus(404);
        assertThat(admin.get("/api/plays/" + FANCY_PLAY)).hasStatus(404);

        // The new Games come with the file's recording defaults; a Player without Plays comes too.
        assertThat(games.findByName("Catan").orElseThrow().defaults())
                .isEqualTo(new RecordingDefaults(false, RecordingMode.WINNER_ONLY, false));
        assertThat(games.findByName("Hanabi").orElseThrow().defaults().cooperative()).isTrue();
        assertThat(games.findByName("Fancy Game")).isEmpty();
        assertThat(players.findByName("Zsófi")).isPresent();
    }

    @Test
    void importingTheSameFileAgainChangesNothing() {
        admin.post("/api/import", request(sample(), chosen(FILE_ANNA, anna), chosen(FILE_DORA, dora), named(FILE_ZSOFI, "Zsófi")));
        UUID zsofi = players.findByName("Zsófi").orElseThrow().id();

        assertThat(admin.post("/api/import", request(sample(), chosen(FILE_ANNA, anna), chosen(FILE_DORA, dora), chosen(FILE_ZSOFI, zsofi))))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"added\": 0, \"alreadyHere\": 4, \"newPlayers\": 0, \"newGames\": 0}");
        assertThat(admin.post("/api/import/review", sample())).bodyJson().isLenientlyEqualTo("""
                {"alreadyHere": 4, "games": [{"name": "Calico"}, {"name": "Catan", "match": "CATALOGUE"},
                 {"name": "Fancy Game"}, {"name": "Golf"}, {"name": "Hanabi", "match": "CATALOGUE"}]}
                """);
    }

    @Test
    void aPlayThatIsHereAlreadyTellsWhoIsWho() {
        admin.post("/api/import", request(sample(), chosen(FILE_ANNA, anna), chosen(FILE_DORA, dora), named(FILE_ZSOFI, "Zsófi")));
        UUID zsofi = players.findByName("Zsófi").orElseThrow().id();

        // The file's Anna is "Player of anna" here, which the names alone wouldn't tell.
        assertThat(admin.post("/api/import/review", sample()))
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"players": [{"name": "Anna", "suggestedPlayerId": "%s"},
                                     {"name": "Dora", "suggestedPlayerId": "%s"},
                                     {"name": "Zsófi", "suggestedPlayerId": "%s"}]}
                        """.formatted(anna, dora, zsofi));
    }

    @Test
    void aPlayThatIsHereAlreadyIsLeftAsItIs() {
        // Recorded here under the id the Golf Play has in the file, with other scores.
        admin.post("/api/plays", play(golf.id(), "POINTS", "{\"lowestScoreWins\": \"true\"}", "2026-01-11", scored(anna, 80), scored(dora, 81))
                .replace("{\"gameId\"", "{\"id\": \"" + GOLF_PLAY + "\", \"gameId\""));

        assertThat(admin.post("/api/import", request(sample(), chosen(FILE_ANNA, anna), chosen(FILE_DORA, dora), named(FILE_ZSOFI, "Zsófi"))))
                .bodyJson()
                .isLenientlyEqualTo("{\"added\": 3, \"alreadyHere\": 1}");
        assertThat(admin.get("/api/plays/" + GOLF_PLAY))
                .bodyJson()
                .isLenientlyEqualTo("{\"playedOn\": \"2026-01-11\", \"results\": [{\"total\": 80}, {\"total\": 81}]}");
    }

    @Test
    void anExportImportedIntoAnEmptyInstallationBringsBackEveryPlay() {
        TestBrowser member = bence;
        member.post("/api/plays", pointsPlay(calico.id(), "2026-03-14", calicoPad(anna, 28, 23, 21), calicoPad(dora, 21, 19, 18)));
        admin.post("/api/plays", play(golf.id(), "POINTS", "{\"lowestScoreWins\": \"true\"}", "2026-03-01", scored(anna, 70), scored(dora, 74)));
        JsonNode before = json(admin.get("/api/export"));

        // The database is lost, and Board Keeper starts again with only its first Admin.
        database.empty();
        UUID newAnna = accounts.playerOf(accounts.admin("anna"));
        TestBrowser newAdmin = accounts.signedIn(mvc, "anna");
        games.addGamesOfModules();
        String file = before.toString();
        JsonNode review = json(newAdmin.post("/api/import/review", file));
        List<String> choices = new ArrayList<>();
        for (JsonNode player : review.get("players")) {
            choices.add(player.has("suggestedPlayerId")
                    ? chosen(player.get("fileId").asString(), UUID.fromString(player.get("suggestedPlayerId").asString()))
                    : named(player.get("fileId").asString(), player.get("name").asString()));
        }

        assertThat(newAdmin.post("/api/import", request(file, choices.toArray(String[]::new))))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"added\": 2, \"refused\": [], \"newPlayers\": 2, \"newGames\": 1}");
        JsonNode after = json(newAdmin.get("/api/export"));
        assertThat(summaryOf(after)).isEqualTo(summaryOf(before));
        assertThat(after.get("plays").get(0).get("recordedBy").asString()).isEqualTo(newAnna.toString());
    }

    @Test
    void whoIsWhoIsCheckedBeforeAnythingIsWritten() {
        assertThat(admin.post("/api/import", request(sample(), chosen(FILE_ANNA, dora), chosen(FILE_DORA, dora), named(FILE_ZSOFI, "Zsófi"))))
                .hasStatus(422)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"imports.playerChosenTwice\"}");
        assertThat(admin.post("/api/import", request(sample(), chosen(FILE_ANNA, anna), named(FILE_DORA, "dora"), named(FILE_ZSOFI, "Zsófi"))))
                .hasStatus(409)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"players.nameTaken\"}");
        assertThat(admin.post("/api/import", request(sample(), chosen(FILE_ANNA, anna), named(FILE_DORA, "Zsófi"), named(FILE_ZSOFI, "zsófi"))))
                .hasStatus(409);
        assertThat(admin.post("/api/import", request(sample(), chosen(FILE_ANNA, anna), chosen(FILE_DORA, dora))))
                .hasStatus(422)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"imports.playersNotChosen\"}");
        assertThat(admin.post("/api/import", request(sample(), chosen(FILE_ANNA, anna), chosen(FILE_DORA, UUID.randomUUID()), named(FILE_ZSOFI, "Zsófi"))))
                .hasStatus(404)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"players.notFound\"}");

        assertThat(admin.get("/api/plays")).bodyJson().isLenientlyEqualTo("{\"plays\": []}");
        assertThat(games.findByName("Catan")).isEmpty();
        assertThat(players.findByName("Zsófi")).isEmpty();
    }

    @Test
    void aFileThisBoardKeeperCannotReadIsRefused() {
        ObjectNode newer = sampleTree();
        newer.put("format", 2);
        assertThat(admin.post("/api/import/review", newer.toString()))
                .hasStatus(422)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"imports.formatTooNew\"}");

        assertThat(admin.post("/api/import/review", "{\"name\": \"Not an export at all\"}"))
                .hasStatus(422)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"imports.notAnExport\"}");

        ObjectNode broken = sampleTree();
        ((ObjectNode) broken.get("plays").get(0)).put("recordedBy", UUID.randomUUID().toString());
        assertThat(admin.post("/api/import/review", broken.toString()))
                .hasStatus(422)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"imports.fileBroken\"}");
    }

    @Test
    void onlyAnAdminMayImport() {
        assertThat(bence.post("/api/import/review", sample())).hasStatus(403);
        assertThat(bence.post("/api/import", request(sample(), chosen(FILE_ANNA, anna), chosen(FILE_DORA, dora), named(FILE_ZSOFI, "Zsófi"))))
                .hasStatus(403);
    }

    /** The first version's sample file, as it is kept with the tests. */
    private static String sample() {
        try (InputStream file = ImportApiTests.class.getResourceAsStream("/exports/format-1.json")) {
            return new String(file.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ObjectNode sampleTree() {
        return (ObjectNode) JSON.readTree(sample());
    }

    private static String request(String file, String... choices) {
        return "{\"file\": %s, \"players\": [%s]}".formatted(file, String.join(", ", choices));
    }

    private static String chosen(String fileId, UUID player) {
        return "{\"fileId\": \"%s\", \"playerId\": \"%s\"}".formatted(fileId, player);
    }

    private static String named(String fileId, String name) {
        return "{\"fileId\": \"%s\", \"newName\": \"%s\"}".formatted(fileId, name);
    }

    /** What an Export says about each Play that the ids of this Installation don't change. */
    private static List<String> summaryOf(JsonNode file) {
        List<String> plays = new ArrayList<>();
        for (JsonNode play : file.get("plays")) {
            List<String> results = new ArrayList<>();
            for (JsonNode result : play.get("results")) {
                results.add(result.get("scores") + " " + result.get("total") + " " + result.get("placing"));
            }
            plays.add(play.get("id").asString() + " " + play.get("playedOn").asString() + " " + play.get("setup") + " "
                    + play.get("recordedAt").asString() + " " + results);
        }
        return plays;
    }
}
