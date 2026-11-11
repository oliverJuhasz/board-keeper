package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.INVITE;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.oliverjuhasz.boardkeeper.TestBrowser;
import io.github.oliverjuhasz.boardkeeper.TestDatabase;
import io.github.oliverjuhasz.boardkeeper.TestcontainersConfiguration;
import io.github.oliverjuhasz.boardkeeper.access.Permission;
import io.github.oliverjuhasz.boardkeeper.players.Players;
import java.time.OffsetDateTime;
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
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class InviteTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private TestDatabase database;

    @Autowired
    private TestAccounts accounts;

    @Autowired
    private Players players;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private DSLContext dsl;

    private TestBrowser admin;
    private UUID dora;

    @BeforeEach
    void startWithAnAdminAndAPlayerWithoutAnAccount() {
        database.empty();
        accounts.admin("anna");
        admin = accounts.signedIn(mvc, "anna");
        dora = players.create("Dora").id();
    }

    @Test
    void anInviteLetsThePersonBehindAPlayerRegisterAndSignsThemIn() {
        String token = invite(admin, dora);
        assertThat(admin.get("/api/invites"))
                .bodyJson()
                .isLenientlyEqualTo("[{\"playerId\": \"%s\", \"expired\": false}]".formatted(dora));

        // The invited person opens the link in their own browser, not signed in.
        TestBrowser invited = TestBrowser.open(mvc);
        assertThat(invited.get("/api/registration/" + token))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.playerName")
                .isEqualTo("Dora");

        assertThat(invited.post("/api/registration/" + token, registration("dora")))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"account": {"username": "dora", "playerId": "%s", "playerName": "Dora", "language": "nl",
                                     "permissions": ["CREATE_PLAYERS", "CREATE_GAMES", "EDIT_GAMES", "CREATE_PLAYS"]}}
                        """.formatted(dora));

        // They are signed in, the Player is claimed, and the Invite is used up.
        assertThat(invited.get("/api/players")).hasStatusOk();
        assertThat(admin.get("/api/claims")).bodyJson().extractingPath("$.playerIds").asArray().contains(dora.toString());
        assertThat(admin.get("/api/invites")).bodyJson().isStrictlyEqualTo("[]");
    }

    @Test
    void theLinkWorksOnlyOnce() {
        String token = invite(admin, dora);
        TestBrowser.open(mvc).post("/api/registration/" + token, registration("dora"));

        assertNotValid(TestBrowser.open(mvc).get("/api/registration/" + token));
        assertNotValid(TestBrowser.open(mvc).post("/api/registration/" + token, registration("dora2")));
    }

    @Test
    void anInviteExpires() {
        String token = invite(admin, dora);
        dsl.update(INVITE).set(INVITE.EXPIRES_AT, OffsetDateTime.now().minusMinutes(1)).execute();

        assertNotValid(TestBrowser.open(mvc).get("/api/registration/" + token));
        assertNotValid(TestBrowser.open(mvc).post("/api/registration/" + token, registration("dora")));
        assertThat(admin.get("/api/invites")).bodyJson().isLenientlyEqualTo("[{\"expired\": true}]");
    }

    @Test
    void aCancelledInviteNoLongerWorks() throws Exception {
        String token = invite(admin, dora);
        String inviteId = JSON.readTree(admin.get("/api/invites").getResponse().getContentAsString())
                .get(0)
                .get("id")
                .asString();

        assertThat(admin.delete("/api/invites/" + inviteId)).hasStatus(204);

        assertNotValid(TestBrowser.open(mvc).get("/api/registration/" + token));
        assertThat(admin.delete("/api/invites/" + inviteId)).hasStatus(404);
    }

    @Test
    void aNewInviteReplacesTheEarlierOneForTheSamePlayer() {
        String first = invite(admin, dora);
        String second = invite(admin, dora);

        assertNotValid(TestBrowser.open(mvc).get("/api/registration/" + first));
        assertThat(TestBrowser.open(mvc).get("/api/registration/" + second)).hasStatusOk();
    }

    @Test
    void aPlayerWithAnAccountCannotBeInvited() {
        UUID bence = accounts.playerOf(accounts.member("bence"));

        assertThat(admin.post("/api/invites", "{\"playerId\": \"%s\"}".formatted(bence)))
                .hasStatus(409)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"invites.playerHasAccount\"}");
    }

    @Test
    void onlyAnAdminCanInvite() {
        accounts.member("bence");
        TestBrowser member = accounts.signedIn(mvc, "bence");

        assertThat(member.post("/api/invites", "{\"playerId\": \"%s\"}".formatted(dora))).hasStatus(403);
        assertThat(member.get("/api/invites")).hasStatus(403);
    }

    @Test
    void anInviteCanGiveARoleOtherThanMember() {
        UUID adminRole = roles.idOf(BuiltInRole.ADMIN);

        String token = tokenOf(
                admin.post("/api/invites", "{\"playerId\": \"%s\", \"roleId\": \"%s\"}".formatted(dora, adminRole)));

        assertThat(TestBrowser.open(mvc).post("/api/registration/" + token, registration("dora")))
                .bodyJson()
                .extractingPath("$.account.permissions")
                .asArray()
                .contains("MANAGE_ROLES");
    }

    @Test
    void anInviteCannotGiveARoleThatDoesNotExist() {
        assertThat(admin.post(
                        "/api/invites", "{\"playerId\": \"%s\", \"roleId\": \"%s\"}".formatted(dora, UUID.randomUUID())))
                .hasStatus(404)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"roles.notFound\"}");
    }

    @Test
    void aTakenUsernameIsRefusedAndLeavesTheInviteOpen() {
        String token = invite(admin, dora);
        TestBrowser invited = TestBrowser.open(mvc);

        assertThat(invited.post("/api/registration/" + token, registration("ANNA")))
                .hasStatus(409)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"accounts.usernameTaken\"}");
        assertThat(invited.post("/api/registration/" + token, registration("dora"))).hasStatusOk();
    }

    @Test
    void registeringNeedsAPasswordOfSixteenCharacters() {
        String token = invite(admin, dora);
        String request = registration("dora").replace(TestAccounts.PASSWORD, "fifteen chars..");

        assertThat(TestBrowser.open(mvc).post("/api/registration/" + token, request))
                .hasStatus(400)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"request.invalid\", \"errors\": [{\"field\": \"password\", \"code\": \"size\"}]}");
    }

    @Test
    void listsTheRolesWithTheirPermissions() {
        assertThat(admin.get("/api/roles"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        [{"name": "Admin", "builtIn": "ADMIN"},
                         {"name": "Member", "builtIn": "MEMBER",
                          "permissions": ["CREATE_PLAYERS", "CREATE_GAMES", "EDIT_GAMES", "CREATE_PLAYS"]}]
                        """);
        assertThat(admin.get("/api/roles")).bodyJson().extractingPath("$[0].permissions").asArray().hasSize(Permission.values().length);
    }

    private String invite(TestBrowser browser, UUID playerId) {
        return tokenOf(browser.post("/api/invites", "{\"playerId\": \"%s\"}".formatted(playerId)));
    }

    private static String tokenOf(MvcTestResult created) {
        assertThat(created).hasStatusOk();
        try {
            return JSON.readTree(created.getResponse().getContentAsString()).get("token").asString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void assertNotValid(MvcTestResult result) {
        assertThat(result).hasStatus(404).bodyJson().isLenientlyEqualTo("{\"code\": \"invites.notValid\"}");
    }

    private static String registration(String username) {
        return """
                {"username": "%s", "password": "%s", "language": "nl"}
                """.formatted(username, TestAccounts.PASSWORD);
    }
}
