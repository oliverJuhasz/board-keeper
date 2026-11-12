package io.github.oliverjuhasz.boardkeeper.plays;

import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.ROLE_PERMISSION;
import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.COOPERATIVE;
import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.idOf;
import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.json;
import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.notScored;
import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.pointsPlay;
import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.scored;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.oliverjuhasz.boardkeeper.TestBrowser;
import io.github.oliverjuhasz.boardkeeper.TestDatabase;
import io.github.oliverjuhasz.boardkeeper.TestcontainersConfiguration;
import io.github.oliverjuhasz.boardkeeper.access.Permission;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.TestAccounts;
import io.github.oliverjuhasz.boardkeeper.gameapi.RecordingMode;
import io.github.oliverjuhasz.boardkeeper.games.Game.RecordingDefaults;
import io.github.oliverjuhasz.boardkeeper.games.Games;
import io.github.oliverjuhasz.boardkeeper.players.Players;
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

/** Changing a Play means editing or deleting it. Who may depends on the Play. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ChangingPlaysApiTests {

    private static final RecordingDefaults POINTS = new RecordingDefaults(false, RecordingMode.POINTS, false);

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
        calico = games.create("Calico", POINTS).id();
    }

    @Test
    void whoRecordedAPlayCanEditEverythingAboutItButItsGame() {
        // Bence records a Play that he did not take part in.
        MvcTestResult recorded =
                member.post("/api/plays", pointsPlay(calico, "2026-03-14", scored(anna, 61), scored(dora, 74)));
        String id = idOf(recorded);

        String changed = """
                {"gameId": "%s", "playedOn": "2026-03-15", "location": "At Dora's", "notes": "Corrected.",
                 "recordingMode": "POINTS", "setup": {"lowestScoreWins": "true"},
                 "players": [%s, %s, %s]}
                """.formatted(calico, scored(dora, 74), scored(bence, 30), scored(anna, 61));
        MvcTestResult answer = member.put("/api/plays/" + id, changed);

        String expected = """
                {"id": "%s", "game": {"name": "Calico"}, "playedOn": "2026-03-15", "location": "At Dora's",
                 "notes": "Corrected.", "setup": {"lowestScoreWins": "true"},
                 "results": [{"player": {"name": "Dora"}, "total": 74, "placing": 3, "won": false},
                             {"player": {"name": "Player of bence"}, "total": 30, "placing": 1, "won": true},
                             {"player": {"name": "Player of anna"}, "total": 61, "placing": 2, "won": false}],
                 "recordedBy": {"name": "Player of bence"}, "canChange": true}
                """.formatted(id);
        assertThat(answer).hasStatusOk().bodyJson().isLenientlyEqualTo(expected);
        assertThat(member.get("/api/plays/" + id)).bodyJson().isLenientlyEqualTo(expected);
        // When it was recorded stays as it was.
        assertThat(json(answer).get("recordedAt")).isEqualTo(json(recorded).get("recordedAt"));
    }

    @Test
    void aPlayCanChangeBetweenCompetitiveAndCooperative() {
        String id = idOf(member.post("/api/plays", pointsPlay(calico, "2026-03-14", scored(anna, 1), scored(bence, 2))));

        String cooperative = """
                {"gameId": "%s", "playedOn": "2026-03-14", "recordingMode": "POINTS", "setup": %s,
                 "players": [%s, %s], "group": {"won": true, "scores": {"total": 9}}}
                """.formatted(calico, COOPERATIVE, notScored(anna), notScored(bence));
        MvcTestResult asCooperative = member.put("/api/plays/" + id, cooperative);
        assertThat(asCooperative)
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"cooperative": true, "group": {"won": true, "total": 9},
                         "results": [{"scores": {}, "won": true}, {"scores": {}, "won": true}]}
                        """);
        assertThat(asCooperative).bodyJson().doesNotHavePath("$.results[0].placing");

        MvcTestResult asCompetitive =
                member.put("/api/plays/" + id, pointsPlay(calico, "2026-03-14", scored(anna, 1), scored(bence, 2)));
        assertThat(asCompetitive)
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"cooperative\": false, \"results\": [{\"placing\": 2}, {\"placing\": 1}]}");
        assertThat(asCompetitive).bodyJson().doesNotHavePath("$.group");
    }

    @Test
    void aPlayerWhoTookPartCanChangeAPlaySomeoneElseRecorded() {
        String id = idOf(admin.post("/api/plays", pointsPlay(calico, "2026-03-14", scored(bence, 1), scored(dora, 2))));

        assertThat(member.get("/api/plays/" + id)).bodyJson().extractingPath("$.canChange").isEqualTo(true);
        assertThat(member.put(
                        "/api/plays/" + id, pointsPlay(calico, "2026-03-14", scored(bence, 5), scored(dora, 2))))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"results\": [{\"placing\": 1}, {\"placing\": 2}], \"recordedBy\": {\"name\": \"Player of anna\"}}");
        assertThat(member.delete("/api/plays/" + id)).hasStatus(204);
    }

    @Test
    void aMemberCannotChangeAPlayTheyNeitherRecordedNorTookPartIn() {
        String original = pointsPlay(calico, "2026-03-14", scored(anna, 1), scored(dora, 2));
        String id = idOf(admin.post("/api/plays", original));

        assertThat(member.get("/api/plays/" + id)).bodyJson().extractingPath("$.canChange").isEqualTo(false);
        assertThat(member.get("/api/plays")).bodyJson().extractingPath("$.plays[0].canChange").isEqualTo(false);
        assertThat(member.put("/api/plays/" + id, pointsPlay(calico, "2026-03-14", scored(anna, 9), scored(dora, 2))))
                .hasStatus(403)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"access.denied\"}");
        assertThat(member.delete("/api/plays/" + id)).hasStatus(403);

        // Nothing happened to it.
        assertThat(admin.get("/api/plays/" + id))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"results\": [{\"total\": 1}, {\"total\": 2}], \"canChange\": true}");
    }

    @Test
    void thePermissionForAnyPlayCoversPlaysOfOthers() {
        accounts.memberWith("csilla", Permission.CHANGE_ANY_PLAY);
        TestBrowser csilla = accounts.signedIn(mvc, "csilla");
        String id = idOf(admin.post("/api/plays", pointsPlay(calico, "2026-03-14", scored(anna, 1), scored(dora, 2))));

        assertThat(csilla.get("/api/plays/" + id)).bodyJson().extractingPath("$.canChange").isEqualTo(true);
        assertThat(csilla.put("/api/plays/" + id, pointsPlay(calico, "2026-03-13", scored(anna, 1), scored(dora, 2))))
                .hasStatusOk();
        assertThat(csilla.delete("/api/plays/" + id)).hasStatus(204);
    }

    @Test
    void withoutThePermissionsNotEvenTheRecorderCanChangeAPlay() {
        String id = idOf(member.post("/api/plays", pointsPlay(calico, "2026-03-14", scored(bence, 1), scored(dora, 2))));
        // Take both away from the Member Role, one after the other.
        dsl.deleteFrom(ROLE_PERMISSION).where(ROLE_PERMISSION.PERMISSION.eq("CHANGE_PLAYS_RECORDED")).execute();
        assertThat(member.get("/api/plays/" + id)).bodyJson().extractingPath("$.canChange").isEqualTo(true);

        dsl.deleteFrom(ROLE_PERMISSION).where(ROLE_PERMISSION.PERMISSION.eq("CHANGE_PLAYS_TAKEN_PART_IN")).execute();
        assertThat(member.get("/api/plays/" + id)).bodyJson().extractingPath("$.canChange").isEqualTo(false);
        assertThat(member.delete("/api/plays/" + id)).hasStatus(403);
    }

    @Test
    void aChangedPlayIsCheckedLikeANewOne() {
        String id = idOf(member.post("/api/plays", pointsPlay(calico, "2026-03-14", scored(bence, 1), scored(dora, 2))));

        assertThat(member.put("/api/plays/" + id, pointsPlay(calico, "2026-03-14", scored(bence, 1))))
                .hasStatus(422)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"plays.invalidEntry\", \"reason\": \"generic.invalid.tooFewPlayers\"}");
        assertThat(member.put("/api/plays/" + id, pointsPlay(calico, "2999-01-01", scored(bence, 1), scored(dora, 2))))
                .hasStatus(422)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"plays.dateInFuture\"}");
        assertThat(member.put(
                        "/api/plays/" + id,
                        pointsPlay(calico, "2026-03-14", scored(bence, 1), scored(UUID.randomUUID(), 2))))
                .hasStatus(404)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"players.notFound\"}");

        assertThat(member.get("/api/plays/" + id))
                .bodyJson()
                .isLenientlyEqualTo("{\"playedOn\": \"2026-03-14\", \"results\": [{\"total\": 1}, {\"total\": 2}]}");
    }

    @Test
    void thePlayCannotBeMovedToAnotherGame() {
        UUID azul = games.create("Azul", POINTS).id();
        String id = idOf(member.post("/api/plays", pointsPlay(calico, "2026-03-14", scored(bence, 1), scored(dora, 2))));

        assertThat(member.put("/api/plays/" + id, pointsPlay(azul, "2026-03-14", scored(bence, 1), scored(dora, 2))))
                .hasStatus(422)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"plays.gameCannotChange\"}");
    }

    @Test
    void aDeletedPlayIsGoneForGoodAndFreesItsGameAndPlayers() {
        String id = idOf(member.post("/api/plays", pointsPlay(calico, "2026-03-14", scored(bence, 1), scored(dora, 2))));

        assertThat(member.delete("/api/plays/" + id)).hasStatus(204);

        assertThat(member.get("/api/plays/" + id)).hasStatus(404);
        assertThat(member.get("/api/plays")).bodyJson().isStrictlyEqualTo("{\"plays\": []}");
        assertThat(admin.delete("/api/games/" + calico)).hasStatus(204);
        assertThat(admin.delete("/api/players/" + dora)).hasStatus(204);
    }

    @Test
    void aPlayThatDoesNotExistCannotBeChanged() {
        String unknown = "/api/plays/" + UUID.randomUUID();

        assertThat(member.put(unknown, pointsPlay(calico, "2026-03-14", scored(bence, 1), scored(dora, 2))))
                .hasStatus(404)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"plays.notFound\"}");
        assertThat(member.delete(unknown)).hasStatus(404);
    }

    @Test
    void isClosedToAnyoneNotSignedIn() {
        String id = idOf(member.post("/api/plays", pointsPlay(calico, "2026-03-14", scored(bence, 1), scored(dora, 2))));

        assertThat(TestBrowser.open(mvc).delete("/api/plays/" + id)).hasStatus(401);
        assertThat(member.get("/api/plays/" + id)).hasStatusOk();
    }
}
