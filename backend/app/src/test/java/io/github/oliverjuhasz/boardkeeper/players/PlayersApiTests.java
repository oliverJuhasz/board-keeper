package io.github.oliverjuhasz.boardkeeper.players;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.oliverjuhasz.boardkeeper.TestBrowser;
import io.github.oliverjuhasz.boardkeeper.TestDatabase;
import io.github.oliverjuhasz.boardkeeper.TestcontainersConfiguration;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.TestAccounts;
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
class PlayersApiTests {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private TestDatabase database;

    @Autowired
    private TestAccounts accounts;

    @Autowired
    private Players players;

    private UUID annasPlayer;
    private TestBrowser admin;
    private TestBrowser member;

    @BeforeEach
    void startWithAnAdminAndAMember() {
        database.empty();
        annasPlayer = accounts.playerOf(accounts.admin("anna"));
        accounts.member("bence");
        admin = accounts.signedIn(mvc, "anna");
        member = accounts.signedIn(mvc, "bence");
    }

    @Test
    void listsThePlayersByNameWhateverTheirCapitals() {
        players.create("zsofi");
        players.create("Dora");

        assertThat(member.get("/api/players"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[*].name")
                .asArray()
                .containsExactly("Dora", "Player of anna", "Player of bence", "zsofi");
    }

    @Test
    void isClosedToAnyoneNotSignedIn() {
        assertThat(TestBrowser.open(mvc).get("/api/players")).hasStatus(401);
    }

    @Test
    void aMemberCanCreateAPlayer() {
        assertThat(member.post("/api/players", "{\"name\": \"  Dora  \"}"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.name")
                .isEqualTo("Dora");
        assertThat(players.all()).extracting(Player::name).contains("Dora");
    }

    @Test
    void twoPlayersCannotShareANameEvenInDifferentCapitals() {
        players.create("Dora");

        assertThat(member.post("/api/players", "{\"name\": \"DORA\"}"))
                .hasStatus(409)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"players.nameTaken\"}");
    }

    @Test
    void aPlayerNeedsAName() {
        assertThat(member.post("/api/players", "{\"name\": \"   \"}"))
                .hasStatus(400)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"request.invalid\", \"errors\": [{\"field\": \"name\"}]}");
    }

    @Test
    void renamingAndRemovingNeedTheirOwnPermissions() {
        UUID dora = players.create("Dora").id();

        // A Member holds "Create Players", but not "Edit Players" or "Remove Players".
        assertThat(member.put("/api/players/" + dora, "{\"name\": \"Dóra\"}"))
                .hasStatus(403)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"access.denied\"}");
        assertThat(member.delete("/api/players/" + dora)).hasStatus(403);
        assertThat(players.find(dora)).map(Player::name).contains("Dora");
    }

    @Test
    void anAdminCanRenameAPlayer() {
        UUID dora = players.create("dora").id();

        assertThat(admin.put("/api/players/" + dora, "{\"name\": \"Dora\"}"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.name")
                .isEqualTo("Dora");
        assertThat(players.find(dora)).map(Player::name).contains("Dora");
    }

    @Test
    void aPlayerCannotBeRenamedToAnotherPlayersName() {
        UUID dora = players.create("Dora").id();
        players.create("Zsofi");

        assertThat(admin.put("/api/players/" + dora, "{\"name\": \"zsofi\"}"))
                .hasStatus(409)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"players.nameTaken\"}");
    }

    @Test
    void renamingAPlayerThatDoesNotExistIsNotFound() {
        assertThat(admin.put("/api/players/" + UUID.randomUUID(), "{\"name\": \"Dora\"}"))
                .hasStatus(404)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"players.notFound\"}");
    }

    @Test
    void anAdminCanRemoveAPlayerThatNothingRefersTo() {
        UUID dora = players.create("Dora").id();

        assertThat(admin.delete("/api/players/" + dora)).hasStatus(204);

        assertThat(players.find(dora)).isEmpty();
    }

    @Test
    void aPlayerWithAnAccountCannotBeRemoved() {
        assertThat(admin.delete("/api/players/" + annasPlayer))
                .hasStatus(409)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"players.inUse\"}");
        assertThat(players.find(annasPlayer)).isPresent();
    }
}
