package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.INVITE;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.oliverjuhasz.boardkeeper.TestBrowser;
import io.github.oliverjuhasz.boardkeeper.TestDatabase;
import io.github.oliverjuhasz.boardkeeper.TestcontainersConfiguration;
import io.github.oliverjuhasz.boardkeeper.access.Permission;
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
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RoleAdministrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private TestDatabase database;

    @Autowired
    private TestAccounts accounts;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private Players players;

    @Autowired
    private DSLContext dsl;

    private TestBrowser admin;

    @BeforeEach
    void startWithAnAdmin() {
        database.empty();
        accounts.admin("anna");
        admin = accounts.signedIn(mvc, "anna");
    }

    @Test
    void anAdminCanCreateARole() {
        assertThat(admin.post("/api/roles", role("Scorekeeper", "CREATE_PLAYS", "EDIT_PLAYERS")))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"name\": \"Scorekeeper\", \"permissions\": [\"EDIT_PLAYERS\", \"CREATE_PLAYS\"]}");

        // The built-in Roles come first, then the others.
        assertThat(admin.get("/api/roles"))
                .bodyJson()
                .extractingPath("$[*].name")
                .asArray()
                .containsExactly("Admin", "Member", "Scorekeeper");
    }

    @Test
    void twoRolesCannotShareAName() {
        admin.post("/api/roles", role("Scorekeeper"));

        assertThat(admin.post("/api/roles", role("scorekeeper")))
                .hasStatus(409)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"roles.nameTaken\"}");
        assertThat(admin.post("/api/roles", role("member"))).hasStatus(409);
    }

    @Test
    void aRoleCanBeRenamedAndGivenOtherPermissions() {
        String id = idOf(admin.post("/api/roles", role("Scorekeeper", "CREATE_PLAYS")));

        assertThat(admin.put("/api/roles/" + id, role("Organiser", "CREATE_PLAYERS", "REMOVE_PLAYERS")))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"name\": \"Organiser\", \"permissions\": [\"CREATE_PLAYERS\", \"REMOVE_PLAYERS\"]}");
        assertThat(roles.find(UUID.fromString(id)).orElseThrow().permissions())
                .containsExactlyInAnyOrder(Permission.CREATE_PLAYERS, Permission.REMOVE_PLAYERS);
    }

    @Test
    void theMemberRoleKeepsItsNameButItsPermissionsCanChange() {
        UUID member = roles.idOf(BuiltInRole.MEMBER);

        assertThat(admin.put("/api/roles/" + member, role("Something else", "CREATE_PLAYS")))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"name\": \"Member\", \"builtIn\": \"MEMBER\", \"permissions\": [\"CREATE_PLAYS\"]}");
    }

    @Test
    void theAdminRoleCannotBeChanged() {
        assertThat(admin.put("/api/roles/" + roles.idOf(BuiltInRole.ADMIN), role("Admin", "CREATE_PLAYS")))
                .hasStatus(409)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"roles.adminCannotChange\"}");
    }

    @Test
    void deletingARoleTakesItFromItsAccountsAndFromOpenInvites() {
        accounts.memberWith("bence", Permission.EDIT_PLAYERS);
        TestBrowser bence = accounts.signedIn(mvc, "bence");
        UUID bencesRole = roles.findIdByName("Role of bence").orElseThrow();
        UUID dora = players.create("Dora").id();
        admin.post("/api/invites", "{\"playerId\": \"%s\", \"roleId\": \"%s\"}".formatted(dora, bencesRole));
        assertThat(bence.put("/api/players/" + dora, "{\"name\": \"Dóra\"}")).hasStatusOk();

        assertThat(admin.delete("/api/roles/" + bencesRole)).hasStatus(204);

        // Bence lost the Permission at once, and the Invite now gives the Member Role.
        assertThat(bence.put("/api/players/" + dora, "{\"name\": \"Dora\"}")).hasStatus(403);
        assertThat(dsl.select(INVITE.ROLE_ID).from(INVITE).fetchSingle().value1()).isEqualTo(roles.idOf(BuiltInRole.MEMBER));
    }

    @Test
    void theBuiltInRolesCannotBeDeleted() {
        assertThat(admin.delete("/api/roles/" + roles.idOf(BuiltInRole.MEMBER)))
                .hasStatus(409)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"roles.builtInCannotDelete\"}");
        assertThat(admin.delete("/api/roles/" + roles.idOf(BuiltInRole.ADMIN))).hasStatus(409);
    }

    @Test
    void onlyAnAdminCanManageRoles() {
        accounts.member("bence");
        TestBrowser member = accounts.signedIn(mvc, "bence");

        assertThat(member.post("/api/roles", role("Scorekeeper")))
                .hasStatus(403)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"access.denied\"}");
        // Everyone signed in can read the Roles.
        assertThat(member.get("/api/roles")).hasStatusOk();
    }

    @Test
    void aRoleCannotHoldAPermissionThatIsForAdminsOnly() {
        for (String adminOnly : new String[] {"MANAGE_ROLES", "MANAGE_ACCOUNTS", "MANAGE_INVITES", "RECALCULATE_PLAYS", "EXPORT", "IMPORT"}) {
            assertThat(admin.post("/api/roles", role("Sneaky", "CREATE_PLAYS", adminOnly)))
                    .as(adminOnly)
                    .hasStatus(409)
                    .bodyJson()
                    .isLenientlyEqualTo("{\"code\": \"roles.adminOnlyPermission\"}");
        }
        UUID member = roles.idOf(BuiltInRole.MEMBER);
        assertThat(admin.put("/api/roles/" + member, role("Member", "MANAGE_ACCOUNTS"))).hasStatus(409);
    }

    @Test
    void anAdminOnlyPermissionNeverComesFromAnotherRoleWhateverTheDatabaseHolds() {
        // memberWith writes straight to the database, past the rule above.
        accounts.memberWith("bence", Permission.MANAGE_ROLES, Permission.EDIT_PLAYERS);
        TestBrowser bence = accounts.signedIn(mvc, "bence");

        assertThat(bence.get("/api/session"))
                .bodyJson()
                .extractingPath("$.account.permissions")
                .asArray()
                .contains("EDIT_PLAYERS")
                .doesNotContain("MANAGE_ROLES");
        assertThat(bence.post("/api/roles", role("Helper", "CREATE_PLAYERS"))).hasStatus(403);
    }

    private static String role(String name, String... permissions) {
        String list = permissions.length == 0 ? "" : "\"" + String.join("\", \"", permissions) + "\"";
        return "{\"name\": \"%s\", \"permissions\": [%s]}".formatted(name, list);
    }

    private static String idOf(MvcTestResult created) {
        assertThat(created).hasStatusOk();
        try {
            return JSON.readTree(created.getResponse().getContentAsString()).get("id").asString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
