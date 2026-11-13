package io.github.oliverjuhasz.boardkeeper.plays;

import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.PLAY;
import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.ROLE_PERMISSION;
import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.COMPETITIVE;
import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.COOPERATIVE;
import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.idOf;
import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.notScored;
import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.scored;
import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.tieWinner;
import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.winner;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.oliverjuhasz.boardkeeper.TestBrowser;
import io.github.oliverjuhasz.boardkeeper.TestDatabase;
import io.github.oliverjuhasz.boardkeeper.TestcontainersConfiguration;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.TestAccounts;
import io.github.oliverjuhasz.boardkeeper.gameapi.RecordingMode;
import io.github.oliverjuhasz.boardkeeper.games.Game.RecordingDefaults;
import io.github.oliverjuhasz.boardkeeper.games.Games;
import io.github.oliverjuhasz.boardkeeper.players.Players;
import java.time.LocalDate;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PlaysApiTests {


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

    @Autowired
    private DSLContext dsl;

    private TestBrowser admin;
    private TestBrowser member;
    private UUID calico;
    private UUID anna;
    private UUID bence;
    private UUID dora;

    @BeforeEach
    void startWithAGameAndThreePlayers() {
        database.empty();
        anna = accounts.playerOf(accounts.admin("anna"));
        bence = accounts.playerOf(accounts.member("bence"));
        dora = players.create("Dora").id();
        admin = accounts.signedIn(mvc, "anna");
        member = accounts.signedIn(mvc, "bence");
        calico = games.create("Calico", new RecordingDefaults(false, RecordingMode.POINTS, false)).id();
    }

    @Test
    void aPlayRecordedUnderAnIdIsRecordedOnceHoweverOftenItIsSent() {
        UUID id = UUID.fromString("019a7c00-0000-7000-8000-000000000001");
        String sent = withId(id, play("POINTS", COMPETITIVE, "2026-03-14", scored(dora, 61), scored(anna, 74)));

        assertThat(member.post("/api/plays", sent)).hasStatusOk().bodyJson().extractingPath("$.id").isEqualTo(id.toString());
        // A phone that never heard the answer sends it again.
        assertThat(member.post("/api/plays", sent))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"id\": \"%s\", \"results\": [{\"total\": 61}, {\"total\": 74}]}".formatted(id));
        assertThat(dsl.fetchCount(PLAY)).isEqualTo(1);
    }

    @Test
    void sendingAPlayAgainGivesBackWhatWasRecordedThen() {
        UUID id = UUID.fromString("019a7c00-0000-7000-8000-000000000002");
        member.post("/api/plays", withId(id, play("POINTS", COMPETITIVE, "2026-03-14", scored(dora, 61), scored(anna, 74))));

        assertThat(member.post("/api/plays", withId(id, play("POINTS", COMPETITIVE, "2026-03-14", scored(dora, 1), scored(anna, 2)))))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"results\": [{\"total\": 61}, {\"total\": 74}]}");
    }

    @Test
    void anotherAccountCannotRecordUnderAPlaysId() {
        UUID id = UUID.fromString("019a7c00-0000-7000-8000-000000000003");
        member.post("/api/plays", withId(id, play("POINTS", COMPETITIVE, "2026-03-14", scored(dora, 61), scored(anna, 74))));

        assertThat(admin.post("/api/plays", withId(id, play("POINTS", COMPETITIVE, "2026-03-14", scored(dora, 5), scored(anna, 4)))))
                .hasStatus(409)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"plays.idTaken\"}");
        assertThat(dsl.fetchCount(PLAY)).isEqualTo(1);
    }

    /** The same request with the id the device chose for the new Play. */
    private static String withId(UUID id, String body) {
        return body.replaceFirst("\\{", "{\"id\": \"%s\", ".formatted(id));
    }

    @Test
    void recordsACompetitivePlayInPointsWithItsPlacings() {
        MvcTestResult recorded = member.post(
                "/api/plays",
                play("POINTS", COMPETITIVE, "2026-03-14", scored(dora, 61), scored(anna, 74), scored(bence, 61)));

        String expected = """
                {"game": {"id": "%s", "name": "Calico"},
                 "playedOn": "2026-03-14",
                 "recordingMode": "POINTS",
                 "setup": {},
                 "cooperative": false,
                 "results": [
                   {"player": {"id": "%s", "name": "Dora"}, "scores": {"total": 61}, "markedWinner": false,
                    "total": 61, "placing": 2, "won": false},
                   {"player": {"name": "Player of anna"}, "total": 74, "placing": 1, "won": true},
                   {"player": {"name": "Player of bence"}, "total": 61, "placing": 2, "won": false}],
                 "recordedBy": {"id": "%s", "name": "Player of bence"}}
                """.formatted(calico, dora, bence);
        assertThat(recorded).hasStatusOk().bodyJson().isLenientlyEqualTo(expected);
        assertThat(recorded).bodyJson().doesNotHavePath("$.group");

        // Reading it back gives the same Play.
        assertThat(member.get("/api/plays/" + idOf(recorded))).hasStatusOk().bodyJson().isLenientlyEqualTo(expected);
    }

    @Test
    void theLowestScoreCanWin() {
        assertThat(member.post(
                        "/api/plays",
                        play("POINTS", "{\"lowestScoreWins\": \"true\"}", "2026-03-14", scored(anna, 74), scored(bence, 61))))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"setup": {"lowestScoreWins": "true"},
                         "results": [{"placing": 2, "won": false}, {"placing": 1, "won": true}]}
                        """);
    }

    @Test
    void theRecorderDecidesATieForFirstPlace() {
        assertThat(member.post(
                        "/api/plays",
                        play("POINTS", COMPETITIVE, "2026-03-14", scored(anna, 70), tieWinner(bence, 70), scored(dora, 12))))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"results": [
                           {"markedWinner": false, "placing": 2, "won": false},
                           {"markedWinner": true, "placing": 1, "won": true},
                           {"markedWinner": false, "placing": 3, "won": false}]}
                        """);
    }

    @Test
    void recordsOnlyTheWinnerWithoutTotalsOrPlacings() {
        MvcTestResult recorded =
                member.post("/api/plays", play("WINNER_ONLY", COMPETITIVE, "2026-03-14", winner(dora), notScored(anna)));

        assertThat(recorded)
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"recordingMode": "WINNER_ONLY",
                         "results": [{"scores": {}, "markedWinner": true, "won": true},
                                     {"scores": {}, "markedWinner": false, "won": false}]}
                        """);
        assertThat(recorded).bodyJson().doesNotHavePath("$.results[0].total");
        assertThat(recorded).bodyJson().doesNotHavePath("$.results[0].placing");
    }

    @Test
    void recordsACooperativePlayWithTheGroupsScore() {
        String body = """
                {"gameId": "%s", "playedOn": "2026-03-14", "recordingMode": "POINTS", "setup": %s,
                 "players": [%s, %s], "group": {"won": false, "scores": {"total": 17}}}
                """.formatted(calico, COOPERATIVE, notScored(anna), notScored(dora));

        MvcTestResult recorded = member.post("/api/plays", body);

        assertThat(recorded)
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"cooperative": true,
                         "group": {"won": false, "scores": {"total": 17}, "total": 17},
                         "results": [{"won": false}, {"won": false}]}
                        """);
        assertThat(recorded).bodyJson().doesNotHavePath("$.results[0].placing");
    }

    @Test
    void aCooperativePlayCanBeASoloPlayThatWasSimplyWon() {
        String body = """
                {"gameId": "%s", "playedOn": "2026-03-14", "recordingMode": "WINNER_ONLY", "setup": %s,
                 "players": [%s], "group": {"won": true}}
                """.formatted(calico, COOPERATIVE, notScored(bence));

        MvcTestResult recorded = member.post("/api/plays", body);

        assertThat(recorded)
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"group\": {\"won\": true, \"scores\": {}}, \"results\": [{\"won\": true}]}");
        assertThat(recorded).bodyJson().doesNotHavePath("$.group.total");
    }

    @Test
    void keepsWhereItWasPlayedAndTheNotesWithoutSpacesAround() {
        String body = """
                {"gameId": "%s", "playedOn": "2026-03-14", "location": "  At Dora's ", "notes": "   ",
                 "recordingMode": "WINNER_ONLY", "setup": {}, "players": [%s, %s]}
                """.formatted(calico, winner(dora), notScored(anna));

        MvcTestResult recorded = member.post("/api/plays", body);

        assertThat(recorded).hasStatusOk().bodyJson().extractingPath("$.location").isEqualTo("At Dora's");
        assertThat(recorded).bodyJson().doesNotHavePath("$.notes");
    }

    @Test
    void whatTheGameModuleRefusesIsReportedWithItsReason() {
        assertThat(member.post("/api/plays", play("WINNER_ONLY", COMPETITIVE, "2026-03-14", notScored(anna), notScored(dora))))
                .hasStatus(422)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"plays.invalidEntry\", \"reason\": \"generic.invalid.winnerMissing\"}");
        assertThat(member.post("/api/plays", play("POINTS", COMPETITIVE, "2026-03-14", scored(anna, 50))))
                .hasStatus(422)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"plays.invalidEntry\", \"reason\": \"generic.invalid.tooFewPlayers\"}");
        assertThat(dsl.fetchCount(PLAY)).isZero();
    }

    @Test
    void aPlayNeedsAGameAndPlayersThatExist() {
        String ofAnUnknownGame = play("POINTS", COMPETITIVE, "2026-03-14", scored(anna, 1), scored(dora, 2))
                .replace(calico.toString(), UUID.randomUUID().toString());
        assertThat(member.post("/api/plays", ofAnUnknownGame))
                .hasStatus(404)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"games.notFound\"}");

        assertThat(member.post(
                        "/api/plays",
                        play("POINTS", COMPETITIVE, "2026-03-14", scored(anna, 1), scored(UUID.randomUUID(), 2))))
                .hasStatus(404)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"players.notFound\"}");
    }

    @Test
    void aPlayerTakesPartInAPlayOnlyOnce() {
        assertThat(member.post("/api/plays", play("POINTS", COMPETITIVE, "2026-03-14", scored(anna, 1), scored(anna, 2))))
                .hasStatus(422)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"plays.playerTwice\"}");
    }

    @Test
    void aPlayNeedsPlayersADateAndARecordingMode() {
        assertThat(member.post("/api/plays", play("POINTS", COMPETITIVE, "2026-03-14")))
                .hasStatus(400)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"request.invalid\", \"errors\": [{\"field\": \"players\"}]}");
        assertThat(member.post(
                        "/api/plays",
                        "{\"gameId\": \"%s\", \"setup\": {}, \"players\": [%s, %s]}"
                                .formatted(calico, winner(anna), notScored(dora))))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .asArray()
                .containsExactlyInAnyOrder("playedOn", "recordingMode");
    }

    @Test
    void aPlayCannotBeDatedInTheFuture() {
        String nextWeek = LocalDate.now().plusDays(7).toString();
        assertThat(member.post("/api/plays", play("POINTS", COMPETITIVE, nextWeek, scored(anna, 1), scored(dora, 2))))
                .hasStatus(422)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"plays.dateInFuture\"}");

        String today = LocalDate.now().toString();
        assertThat(member.post("/api/plays", play("POINTS", COMPETITIVE, today, scored(anna, 1), scored(dora, 2))))
                .hasStatusOk();
    }

    @Test
    void recordingAPlayNeedsThePermission() {
        // Take "Create plays" away from every Role but Admin.
        dsl.deleteFrom(ROLE_PERMISSION).where(ROLE_PERMISSION.PERMISSION.eq("CREATE_PLAYS")).execute();
        String body = play("POINTS", COMPETITIVE, "2026-03-14", scored(anna, 1), scored(dora, 2));

        assertThat(member.post("/api/plays", body)).hasStatus(403);
        assertThat(dsl.fetchCount(PLAY)).isZero();

        MvcTestResult recorded = admin.post("/api/plays", body);
        assertThat(recorded).hasStatusOk();
        // Reading a Play needs no Permission: everyone shares one space (ADR 0005).
        assertThat(member.get("/api/plays/" + idOf(recorded))).hasStatusOk();
    }

    @Test
    void aGameOrAPlayerWithPlaysCannotBeRemoved() {
        member.post("/api/plays", play("POINTS", COMPETITIVE, "2026-03-14", scored(anna, 1), scored(dora, 2)));

        assertThat(admin.delete("/api/games/" + calico))
                .hasStatus(409)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"games.inUse\"}");
        assertThat(admin.delete("/api/players/" + dora))
                .hasStatus(409)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"players.inUse\"}");
    }

    @Test
    void aPlayThatDoesNotExistIsNotFound() {
        assertThat(member.get("/api/plays/" + UUID.randomUUID()))
                .hasStatus(404)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"plays.notFound\"}");
    }

    @Test
    void isClosedToAnyoneNotSignedIn() {
        assertThat(TestBrowser.open(mvc).get("/api/plays/" + UUID.randomUUID())).hasStatus(401);
    }

    private String play(String recordingMode, String setup, String playedOn, String... playerEntries) {
        return PlayRequests.play(calico, recordingMode, setup, playedOn, playerEntries);
    }
}
