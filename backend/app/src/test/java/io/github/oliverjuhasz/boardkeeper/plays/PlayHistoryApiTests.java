package io.github.oliverjuhasz.boardkeeper.plays;

import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.idOf;
import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.json;
import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.pointsPlay;
import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.scored;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.oliverjuhasz.boardkeeper.TestBrowser;
import io.github.oliverjuhasz.boardkeeper.TestDatabase;
import io.github.oliverjuhasz.boardkeeper.TestcontainersConfiguration;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.TestAccounts;
import io.github.oliverjuhasz.boardkeeper.gameapi.RecordingMode;
import io.github.oliverjuhasz.boardkeeper.games.Game.RecordingDefaults;
import io.github.oliverjuhasz.boardkeeper.games.Games;
import io.github.oliverjuhasz.boardkeeper.players.Players;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PlayHistoryApiTests {

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

    private TestBrowser member;
    private UUID calico;
    private UUID azul;
    private UUID anna;
    private UUID bence;
    private UUID dora;

    @BeforeEach
    void startWithTwoGamesAndThreePlayers() {
        database.empty();
        anna = accounts.playerOf(accounts.admin("anna"));
        bence = accounts.playerOf(accounts.member("bence"));
        dora = players.create("Dora").id();
        member = accounts.signedIn(mvc, "bence");
        calico = games.create("Calico", POINTS).id();
        azul = games.create("Azul", POINTS).id();
    }

    @Test
    void listsThePlaysFromTheNewestDayBackAndWithinADayTheLastRecordedFirst() {
        String tenth = record(calico, "2026-03-10", anna, bence);
        String fourteenth = record(calico, "2026-03-14", anna, bence);
        String laterOnTheFourteenth = record(azul, "2026-03-14", anna, dora);
        String first = record(calico, "2026-03-01", anna, bence);

        MvcTestResult history = member.get("/api/plays");

        assertThat(history).hasStatusOk();
        assertThat(idsIn(history)).containsExactly(laterOnTheFourteenth, fourteenth, tenth, first);
        assertThat(history).bodyJson().doesNotHavePath("$.next");
        // Each Play comes complete, as on its own page.
        assertThat(history).bodyJson().isLenientlyEqualTo("""
                {"plays": [
                   {"game": {"name": "Azul"}, "playedOn": "2026-03-14",
                    "results": [{"player": {"name": "Player of anna"}, "total": 2, "placing": 1, "won": true},
                                {"player": {"name": "Dora"}, "total": 1, "placing": 2, "won": false}],
                    "recordedBy": {"name": "Player of bence"}, "canChange": true},
                   {"game": {"name": "Calico"}}, {"game": {"name": "Calico"}}, {"game": {"name": "Calico"}}]}
                """);
    }

    @Test
    void anEmptyHistoryIsAnEmptyList() {
        assertThat(member.get("/api/plays")).hasStatusOk().bodyJson().isStrictlyEqualTo("{\"plays\": []}");
    }

    @Test
    void canBeNarrowedToOneGame() {
        String ofCalico = record(calico, "2026-03-10", anna, bence);
        record(azul, "2026-03-11", anna, bence);

        assertThat(idsIn(member.get("/api/plays?gameId=" + calico))).containsExactly(ofCalico);
        assertThat(idsIn(member.get("/api/plays?gameId=" + UUID.randomUUID()))).isEmpty();
    }

    @Test
    void canBeNarrowedToThePlaysOfOnePlayerOrOfSeveralTogether() {
        String annaAndBence = record(calico, "2026-03-10", anna, bence);
        String annaAndDora = record(calico, "2026-03-11", anna, dora);
        String allThree = record(calico, "2026-03-12", anna, bence, dora);

        assertThat(idsIn(member.get("/api/plays?playerId=" + dora))).containsExactly(allThree, annaAndDora);
        assertThat(idsIn(member.get("/api/plays?playerId=" + dora + "&playerId=" + bence)))
                .containsExactly(allThree);
        assertThat(idsIn(member.get("/api/plays?playerId=" + anna)))
                .containsExactly(allThree, annaAndDora, annaAndBence);
    }

    @Test
    void canBeNarrowedToAPeriodIncludingItsFirstAndLastDay() {
        record(calico, "2026-03-09", anna, bence);
        String tenth = record(calico, "2026-03-10", anna, bence);
        String twelfth = record(calico, "2026-03-12", anna, bence);
        String thirteenth = record(calico, "2026-03-13", anna, bence);

        assertThat(idsIn(member.get("/api/plays?from=2026-03-10&to=2026-03-12"))).containsExactly(twelfth, tenth);
        assertThat(idsIn(member.get("/api/plays?from=2026-03-12"))).containsExactly(thirteenth, twelfth);
        assertThat(idsIn(member.get("/api/plays?to=2026-03-09"))).hasSize(1);
        assertThat(member.get("/api/plays?from=yesterday")).hasStatus(400);
    }

    @Test
    void theFiltersWorkTogether() {
        record(azul, "2026-03-10", anna, dora);
        record(calico, "2026-03-10", anna, bence);
        String wanted = record(calico, "2026-03-10", anna, dora);
        record(calico, "2026-02-10", anna, dora);

        assertThat(idsIn(member.get("/api/plays?gameId=" + calico + "&playerId=" + dora + "&from=2026-03-01")))
                .containsExactly(wanted);
    }

    @Test
    void comesAPageAtATime() {
        // Three Plays on the same day, so a page ends in the middle of a day.
        List<String> newestFirst = new ArrayList<>();
        newestFirst.add(record(calico, "2026-03-01", anna, bence));
        newestFirst.add(record(calico, "2026-03-05", anna, bence));
        newestFirst.add(record(calico, "2026-03-05", anna, bence));
        newestFirst.add(record(calico, "2026-03-05", anna, bence));
        newestFirst.add(record(calico, "2026-03-09", anna, bence));
        newestFirst = newestFirst.reversed();

        // An empty "after" asks for the first page, like none at all.
        MvcTestResult first = member.get("/api/plays?limit=2&after=");
        assertThat(idsIn(first)).isEqualTo(newestFirst.subList(0, 2));

        MvcTestResult second = member.get("/api/plays?limit=2&after=" + json(first).get("next").asString());
        assertThat(idsIn(second)).isEqualTo(newestFirst.subList(2, 4));

        MvcTestResult third = member.get("/api/plays?limit=2&after=" + json(second).get("next").asString());
        assertThat(idsIn(third)).isEqualTo(newestFirst.subList(4, 5));
        assertThat(third).bodyJson().doesNotHavePath("$.next");
    }

    @Test
    void aPageThatEndsExactlyAtTheLastPlayIsTheLastPage() {
        record(calico, "2026-03-01", anna, bence);
        record(calico, "2026-03-02", anna, bence);

        assertThat(member.get("/api/plays?limit=2")).bodyJson().doesNotHavePath("$.next");
    }

    @Test
    void aPlaceInTheHistoryThatMakesNoSenseIsRefused() {
        assertThat(member.get("/api/plays?after=somewhere"))
                .hasStatus(400)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"request.invalid\"}");
    }

    @Test
    void isClosedToAnyoneNotSignedIn() {
        assertThat(TestBrowser.open(mvc).get("/api/plays")).hasStatus(401);
    }

    /** Records a competitive Play in points, in which the first Player named scores highest. */
    private String record(UUID game, String playedOn, UUID... playerIds) {
        String[] entries = new String[playerIds.length];
        for (int i = 0; i < playerIds.length; i++) {
            entries[i] = scored(playerIds[i], playerIds.length - i);
        }
        MvcTestResult recorded = member.post("/api/plays", pointsPlay(game, playedOn, entries));
        assertThat(recorded).hasStatusOk();
        return idOf(recorded);
    }

    private static List<String> idsIn(MvcTestResult history) {
        List<String> ids = new ArrayList<>();
        for (JsonNode play : json(history).get("plays")) {
            ids.add(play.get("id").asString());
        }
        return ids;
    }
}
