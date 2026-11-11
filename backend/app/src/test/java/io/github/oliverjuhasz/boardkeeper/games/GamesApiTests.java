package io.github.oliverjuhasz.boardkeeper.games;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.oliverjuhasz.boardkeeper.TestBrowser;
import io.github.oliverjuhasz.boardkeeper.TestDatabase;
import io.github.oliverjuhasz.boardkeeper.TestcontainersConfiguration;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.TestAccounts;
import io.github.oliverjuhasz.boardkeeper.gameapi.RecordingMode;
import io.github.oliverjuhasz.boardkeeper.games.Game.RecordingDefaults;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GamesApiTests {

    private static final String GENERIC_MODULE_ID = "38b119d2-8f9f-4e20-8068-cba305c06cdd";
    private static final RecordingDefaults POINTS = new RecordingDefaults(false, RecordingMode.POINTS, false);

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private TestDatabase database;

    @Autowired
    private TestAccounts accounts;

    @Autowired
    private Games games;

    private TestBrowser admin;
    private TestBrowser member;

    @BeforeEach
    void startWithAnAdminAndAMember() {
        database.empty();
        accounts.admin("anna");
        accounts.member("bence");
        admin = accounts.signedIn(mvc, "anna");
        member = accounts.signedIn(mvc, "bence");
    }

    @Test
    void listsTheInstalledGameModules() {
        assertThat(member.get("/api/game-modules"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("[{\"id\": \"%s\", \"key\": \"generic\"}]".formatted(GENERIC_MODULE_ID));
    }

    @Test
    void givesTheGameModuleTextsInTheLanguageAskedFor() {
        assertThat(member.get("/api/game-modules/texts/hu"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$['generic.name']")
                .isEqualTo("Általános");
        assertThat(member.get("/api/game-modules/texts/xx")).hasStatus(400);
    }

    @Test
    void listsTheGamesByNameWhateverTheirCapitals() {
        games.create("wyrmspan", POINTS);
        games.create("Calico", POINTS);

        assertThat(member.get("/api/games"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[*].name")
                .asArray()
                .containsExactly("Calico", "wyrmspan");
    }

    @Test
    void aGameAddedByHandIsScoredByTheGenericModule() {
        assertThat(member.post("/api/games", game("  Cascadia  ", false, "POINTS", false)))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"name": "Cascadia", "gameModuleId": "%s",
                         "cooperative": false, "recordingMode": "POINTS", "lowestScoreWins": false}
                        """.formatted(GENERIC_MODULE_ID));
    }

    @Test
    void aGameRemembersHowItsPlaysAreRecorded() {
        member.post("/api/games", game("Golf", false, "POINTS", true));
        member.post("/api/games", game("Pandemic", true, "WINNER_ONLY", false));

        assertThat(member.get("/api/games"))
                .bodyJson()
                .isLenientlyEqualTo("""
                        [{"name": "Golf", "cooperative": false, "recordingMode": "POINTS", "lowestScoreWins": true},
                         {"name": "Pandemic", "cooperative": true, "recordingMode": "WINNER_ONLY", "lowestScoreWins": false}]
                        """);
    }

    @Test
    void lowestScoreWinsOnlyAppliesToACompetitivePlayRecordedInPoints() {
        // Asked for where it has no meaning, it is simply not kept.
        assertThat(member.post("/api/games", game("Hanabi", true, "POINTS", true)))
                .bodyJson()
                .extractingPath("$.lowestScoreWins")
                .isEqualTo(false);
        assertThat(member.post("/api/games", game("Chess", false, "WINNER_ONLY", true)))
                .bodyJson()
                .extractingPath("$.lowestScoreWins")
                .isEqualTo(false);
    }

    @Test
    void twoGamesCannotShareANameEvenInDifferentCapitals() {
        games.create("Calico", POINTS);

        assertThat(member.post("/api/games", game("CALICO", false, "POINTS", false)))
                .hasStatus(409)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"games.nameTaken\"}");
    }

    @Test
    void aGameNeedsANameAndARecordingMode() {
        assertThat(member.post("/api/games", game("  ", false, "POINTS", false)))
                .hasStatus(400)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"request.invalid\", \"errors\": [{\"field\": \"name\"}]}");
        assertThat(member.post("/api/games", "{\"name\": \"Calico\", \"cooperative\": false, \"lowestScoreWins\": false}"))
                .hasStatus(400);
    }

    @Test
    void aMemberCanEditAGame() {
        UUID calico = games.create("calico", POINTS).id();

        assertThat(member.put("/api/games/" + calico, game("Calico", false, "WINNER_ONLY", false)))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"name\": \"Calico\", \"recordingMode\": \"WINNER_ONLY\"}");
        assertThat(games.find(calico).orElseThrow().defaults().recordingMode()).isEqualTo(RecordingMode.WINNER_ONLY);
    }

    @Test
    void aGameCannotBeRenamedToAnotherGamesName() {
        UUID calico = games.create("Calico", POINTS).id();
        games.create("Wyrmspan", POINTS);

        assertThat(member.put("/api/games/" + calico, game("wyrmspan", false, "POINTS", false)))
                .hasStatus(409)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"games.nameTaken\"}");
        assertThat(member.put("/api/games/" + UUID.randomUUID(), game("Other", false, "POINTS", false)))
                .hasStatus(404)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"games.notFound\"}");
    }

    @Test
    void removingAGameNeedsItsOwnPermission() {
        UUID calico = games.create("Calico", POINTS).id();

        // A Member holds "Create games" and "Edit games", but not "Remove games".
        assertThat(member.delete("/api/games/" + calico)).hasStatus(403);
        assertThat(games.find(calico)).isPresent();

        assertThat(admin.delete("/api/games/" + calico)).hasStatus(204);
        assertThat(games.find(calico)).isEmpty();
    }

    @Test
    void isClosedToAnyoneNotSignedIn() {
        assertThat(TestBrowser.open(mvc).get("/api/games")).hasStatus(401);
        assertThat(TestBrowser.open(mvc).get("/api/no-such-thing")).hasStatus(401);
    }

    private static String game(String name, boolean cooperative, String recordingMode, boolean lowestScoreWins) {
        return """
                {"name": "%s", "cooperative": %s, "recordingMode": "%s", "lowestScoreWins": %s}
                """.formatted(name, cooperative, recordingMode, lowestScoreWins);
    }
}
