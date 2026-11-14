package io.github.oliverjuhasz.boardkeeper.plays;

import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.PLAY_RESULT;
import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.calicoPad;
import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.idOf;
import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.pointsPlay;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.oliverjuhasz.boardkeeper.TestBrowser;
import io.github.oliverjuhasz.boardkeeper.TestDatabase;
import io.github.oliverjuhasz.boardkeeper.TestcontainersConfiguration;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.TestAccounts;
import io.github.oliverjuhasz.boardkeeper.games.Games;
import io.github.oliverjuhasz.boardkeeper.players.Players;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Recalculation (ADR 0010): running the Game Module over what was entered for a Play again.
 * A module's scoring that changes is played here by changing what is stored instead.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RecalculationApiTests {

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
    private TestBrowser bence;
    private TestBrowser cecil;
    private UUID anna;
    private UUID dora;
    private UUID calico;

    @BeforeEach
    void startWithAPlayOfCalico() {
        database.empty();
        anna = accounts.playerOf(accounts.admin("anna"));
        accounts.member("bence");
        accounts.member("cecil");
        dora = players.create("Dora").id();
        admin = accounts.signedIn(mvc, "anna");
        bence = accounts.signedIn(mvc, "bence");
        cecil = accounts.signedIn(mvc, "cecil");
        games.addGamesOfModules();
        calico = games.all().stream()
                .filter(game -> game.name().equals("Calico"))
                .findFirst()
                .orElseThrow()
                .id();
    }

    @Test
    void aPlayWhoseResultsStillHoldIsLeftAsItIs() {
        String id = recordedByBence();

        assertThat(bence.post("/api/plays/" + id + "/recalculation", ""))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"changed\": false, \"play\": {\"results\": [{\"total\": 72}, {\"total\": 58}]}}");
    }

    @Test
    void aPlayWhoseResultsNoLongerHoldGetsTheNewOnes() {
        String id = recordedByBence();
        // As if an older version of the module had added up Anna's rows wrongly.
        dsl.update(PLAY_RESULT).set(PLAY_RESULT.TOTAL, 70).set(PLAY_RESULT.PLACING, 2).set(PLAY_RESULT.WON, false)
                .where(PLAY_RESULT.PLAY_ID.eq(UUID.fromString(id)), PLAY_RESULT.PLAYER_ID.eq(anna))
                .execute();

        assertThat(bence.post("/api/plays/" + id + "/recalculation", ""))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"changed": true,
                         "play": {"results": [{"total": 72, "placing": 1, "won": true}, {"total": 58, "placing": 2}]}}
                        """);
        assertThat(bence.get("/api/plays/" + id)).bodyJson().extractingPath("$.results[0].total").isEqualTo(72);
    }

    @Test
    void aPlayTheModuleRefusesNowKeepsItsResults() {
        String id = recordedByBence();
        refuseAnnasButtons(id);

        assertThat(bence.post("/api/plays/" + id + "/recalculation", ""))
                .hasStatus(422)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"plays.invalidEntry\", \"reason\": \"plays.invalid.scoreNotAllowed\"}");
        assertThat(bence.get("/api/plays/" + id)).bodyJson().extractingPath("$.results[0].total").isEqualTo(72);
    }

    @Test
    void onlyWhoeverMayChangeThePlayMayRecalculateIt() {
        String id = recordedByBence();

        // Cecil neither recorded it nor played in it.
        assertThat(cecil.post("/api/plays/" + id + "/recalculation", "")).hasStatus(403);
        assertThat(admin.post("/api/plays/" + id + "/recalculation", "")).hasStatusOk();
    }

    @Test
    void anAdminRecalculatesEveryPlayOfAGameAndHearsWhatChanged() {
        String unchanged = recordedByBence();
        String wrong = recordedByBence();
        String refused = recordedByBence();
        dsl.update(PLAY_RESULT).set(PLAY_RESULT.TOTAL, 70)
                .where(PLAY_RESULT.PLAY_ID.eq(UUID.fromString(wrong)), PLAY_RESULT.PLAYER_ID.eq(anna))
                .execute();
        refuseAnnasButtons(refused);

        assertThat(admin.post("/api/plays/recalculation?gameId=" + calico, ""))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"plays": 3, "changed": 1,
                         "refused": [{"playId": "%s", "playedOn": "2026-03-14", "reason": "plays.invalid.scoreNotAllowed"}]}
                        """.formatted(refused));
        assertThat(admin.get("/api/plays/" + wrong)).bodyJson().extractingPath("$.results[0].total").isEqualTo(72);
        assertThat(admin.get("/api/plays/" + unchanged)).bodyJson().extractingPath("$.results[0].total").isEqualTo(72);
    }

    @Test
    void recalculatingEveryPlayOfAGameIsForAdminsOnly() {
        recordedByBence();

        assertThat(bence.post("/api/plays/recalculation?gameId=" + calico, "")).hasStatus(403);
        assertThat(admin.post("/api/plays/recalculation?gameId=" + UUID.randomUUID(), ""))
                .hasStatus(404)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"games.notFound\"}");
    }

    /** A Play of Calico that Bence recorded: Anna 72, Dora 58. */
    private String recordedByBence() {
        return idOf(bence.post(
                "/api/plays",
                pointsPlay(calico, "2026-03-14", calicoPad(anna, 28, 23, 21), calicoPad(dora, 21, 19, 18))));
    }

    /** As if the module now only took buttons in steps it didn't before. */
    private void refuseAnnasButtons(String id) {
        dsl.update(PLAY_RESULT)
                .set(PLAY_RESULT.SCORES, JSONB.valueOf("{\"designGoals\": 28, \"cats\": 23, \"buttons\": 22}"))
                .where(PLAY_RESULT.PLAY_ID.eq(UUID.fromString(id)), PLAY_RESULT.PLAYER_ID.eq(anna))
                .execute();
    }
}
