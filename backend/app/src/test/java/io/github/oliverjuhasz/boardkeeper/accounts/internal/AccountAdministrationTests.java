package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.oliverjuhasz.boardkeeper.TestBrowser;
import io.github.oliverjuhasz.boardkeeper.TestDatabase;
import io.github.oliverjuhasz.boardkeeper.TestcontainersConfiguration;
import io.github.oliverjuhasz.boardkeeper.access.Permission;
import java.util.UUID;
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
class AccountAdministrationTests {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private TestDatabase database;

    @Autowired
    private TestAccounts accounts;

    @Autowired
    private RoleRepository roles;

    private UUID anna;
    private UUID bence;
    private UUID adminRole;
    private UUID memberRole;
    private TestBrowser admin;

    @BeforeEach
    void startWithAnAdminAndAMember() {
        database.empty();
        anna = accounts.admin("anna");
        bence = accounts.member("bence");
        adminRole = roles.idOf(BuiltInRole.ADMIN);
        memberRole = roles.idOf(BuiltInRole.MEMBER);
        admin = accounts.signedIn(mvc, "anna");
    }

    @Test
    void listsTheAccountsByUsername() {
        assertThat(admin.get("/api/accounts"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        [{"id": "%s", "username": "anna", "roleIds": ["%s"], "disabled": false},
                         {"id": "%s", "username": "bence", "roleIds": ["%s"], "disabled": false}]
                        """.formatted(anna, adminRole, bence, memberRole));
    }

    @Test
    void managingAccountsNeedsItsPermission() {
        TestBrowser member = accounts.signedIn(mvc, "bence");

        assertThat(member.get("/api/accounts")).hasStatus(403);
        assertThat(member.put("/api/accounts/" + bence + "/roles", rolesRequest(adminRole))).hasStatus(403);
    }

    @Test
    void givingARoleCountsAtOnce() {
        TestBrowser member = accounts.signedIn(mvc, "bence");
        assertThat(member.get("/api/accounts")).hasStatus(403);

        assertThat(admin.put("/api/accounts/" + bence + "/roles", rolesRequest(memberRole, adminRole))).hasStatus(204);

        assertThat(member.get("/api/accounts")).hasStatusOk();
    }

    @Test
    void theLastAdminKeepsTheAdminRole() {
        assertThat(admin.put("/api/accounts/" + anna + "/roles", rolesRequest(memberRole)))
                .hasStatus(409)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"accounts.lastAdmin\"}");

        // With a second Admin, the first can step down.
        admin.put("/api/accounts/" + bence + "/roles", rolesRequest(adminRole));
        assertThat(admin.put("/api/accounts/" + anna + "/roles", rolesRequest(memberRole))).hasStatus(204);
    }

    @Test
    void aDisabledAccountIsSignedOutAndCannotSignIn() {
        TestBrowser member = accounts.signedIn(mvc, "bence");

        assertThat(admin.put("/api/accounts/" + bence + "/disabled", "{\"disabled\": true}")).hasStatus(204);

        assertThat(member.get("/api/players")).hasStatus(401);
        assertThat(TestBrowser.open(mvc).post("/api/session", TestAccounts.signInRequest("bence", TestAccounts.PASSWORD)))
                .hasStatus(401);
        assertThat(admin.get("/api/accounts")).bodyJson().extractingPath("$[1].disabled").isEqualTo(true);

        // Enabling it again lets Bence back in.
        assertThat(admin.put("/api/accounts/" + bence + "/disabled", "{\"disabled\": false}")).hasStatus(204);
        assertThat(TestBrowser.open(mvc).post("/api/session", TestAccounts.signInRequest("bence", TestAccounts.PASSWORD)))
                .hasStatusOk();
    }

    @Test
    void nobodyCanDisableTheirOwnAccount() {
        assertThat(admin.put("/api/accounts/" + anna + "/disabled", "{\"disabled\": true}"))
                .hasStatus(409)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"accounts.cannotDisableSelf\"}");
    }

    @Test
    void someoneWhoIsNotAnAdminCannotOverruleOne() {
        accounts.memberWith("dora", Permission.MANAGE_ACCOUNTS);
        TestBrowser dora = accounts.signedIn(mvc, "dora");

        // Dora may manage Accounts, but only within what she holds herself.
        assertBeyondOwn(dora.put("/api/accounts/" + bence + "/roles", rolesRequest(memberRole, adminRole)));
        assertBeyondOwn(dora.put("/api/accounts/" + anna + "/roles", rolesRequest(memberRole)));
        assertBeyondOwn(dora.put("/api/accounts/" + anna + "/disabled", "{\"disabled\": true}"));

        // A Member is within her reach.
        assertThat(dora.put("/api/accounts/" + bence + "/disabled", "{\"disabled\": true}")).hasStatus(204);
    }

    @Test
    void anAccountThatDoesNotExistIsNotFound() {
        assertThat(admin.put("/api/accounts/" + UUID.randomUUID() + "/roles", rolesRequest(memberRole)))
                .hasStatus(404)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"accounts.notFound\"}");
    }

    private static void assertBeyondOwn(MvcTestResult result) {
        assertThat(result).hasStatus(403).bodyJson().isLenientlyEqualTo("{\"code\": \"access.beyondOwnPermissions\"}");
    }

    private static String rolesRequest(UUID... roleIds) {
        StringBuilder ids = new StringBuilder();
        for (UUID id : roleIds) {
            ids.append(ids.isEmpty() ? "" : ", ").append('"').append(id).append('"');
        }
        return "{\"roleIds\": [" + ids + "]}";
    }
}
