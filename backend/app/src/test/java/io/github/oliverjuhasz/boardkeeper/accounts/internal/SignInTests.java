package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.ACCOUNT_ROLE;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.oliverjuhasz.boardkeeper.TestBrowser;
import io.github.oliverjuhasz.boardkeeper.TestDatabase;
import io.github.oliverjuhasz.boardkeeper.TestcontainersConfiguration;
import java.util.UUID;
import org.jooq.DSLContext;
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
class SignInTests {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private TestDatabase database;

    @Autowired
    private TestAccounts accounts;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private DSLContext dsl;

    private TestBrowser browser;

    @BeforeEach
    void startWithOneMember() {
        database.empty();
        accounts.member("Bence");
        browser = TestBrowser.open(mvc);
    }

    @Test
    void nobodyIsSignedInAtFirst() {
        assertThat(browser.get("/api/session")).hasStatusOk().bodyJson().isStrictlyEqualTo("{}");
        assertThat(browser.cookie("XSRF-TOKEN")).isNotNull();
    }

    @Test
    void theApiIsClosedToAnyoneNotSignedIn() {
        assertThat(browser.get("/api/games"))
                .hasStatus(401)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"access.notSignedIn\"}");
    }

    @Test
    void theRightPasswordSignsIn() {
        assertThat(browser.post("/api/session", signIn("Bence", TestAccounts.PASSWORD)))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"account": {"username": "Bence", "playerName": "Player of Bence", "language": "en",
                                     "permissions": ["CREATE_PLAYERS", "CREATE_PLAYS"]}}
                        """);

        assertThat(browser.get("/api/session")).bodyJson().extractingPath("$.account.username").isEqualTo("Bence");
        assertThat(browser.get("/api/games")).hasStatusOk();
    }

    @Test
    void theUsernameMayBeTypedInAnyCapitals() {
        assertThat(browser.post("/api/session", signIn("bENCE", TestAccounts.PASSWORD))).hasStatusOk();
    }

    @Test
    void aWrongPasswordAndAnUnknownUsernameGetTheSameAnswer() {
        assertThat(browser.post("/api/session", signIn("Bence", "not the right password")))
                .hasStatus(401)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"session.badCredentials\"}");
        assertThat(browser.post("/api/session", signIn("nobody", TestAccounts.PASSWORD)))
                .hasStatus(401)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"session.badCredentials\"}");

        assertThat(browser.get("/api/session")).bodyJson().isStrictlyEqualTo("{}");
    }

    @Test
    void aRequestWithoutTheCsrfTokenIsRefused() {
        TestBrowser forged = TestBrowser.open(mvc).withoutCsrfToken();

        assertThat(forged.post("/api/session", signIn("Bence", TestAccounts.PASSWORD))).hasStatus(403);
    }

    @Test
    void signingOutEndsTheSession() {
        browser.post("/api/session", signIn("Bence", TestAccounts.PASSWORD));

        assertThat(browser.delete("/api/session")).hasStatus(204);

        assertThat(browser.get("/api/session")).bodyJson().isStrictlyEqualTo("{}");
        assertThat(browser.get("/api/games")).hasStatus(401);
    }

    @Test
    void staySignedInKeepsTheCookieWhenTheBrowserCloses() {
        browser.post("/api/session", signIn("Bence", TestAccounts.PASSWORD, true));
        // A cookie with a lifetime is kept; one without is dropped when the browser closes.
        assertThat(browser.cookie("SESSION").getMaxAge()).isPositive();

        TestBrowser other = TestBrowser.open(mvc);
        other.post("/api/session", signIn("Bence", TestAccounts.PASSWORD, false));
        assertThat(other.cookie("SESSION").getMaxAge()).isEqualTo(-1);
    }

    @Test
    void aDisabledAccountCannotSignIn() {
        accounts.disable(accounts.member("Dora"));

        assertThat(browser.post("/api/session", signIn("Dora", TestAccounts.PASSWORD)))
                .hasStatus(401)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"session.accountDisabled\"}");
        // Without the right password, nothing reveals that the Account exists.
        assertThat(browser.post("/api/session", signIn("Dora", "not the right password")))
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"session.badCredentials\"}");
    }

    @Test
    void disablingAnAccountSignsItOutAtItsNextRequest() {
        UUID dora = accounts.member("Dora");
        browser.post("/api/session", signIn("Dora", TestAccounts.PASSWORD));
        assertThat(browser.get("/api/games")).hasStatusOk();

        accounts.disable(dora);

        assertThat(browser.get("/api/games")).hasStatus(401);
        assertThat(browser.get("/api/session")).bodyJson().isStrictlyEqualTo("{}");
    }

    @Test
    void aNewRoleCountsFromTheNextRequestWithoutSigningInAgain() {
        UUID dora = accounts.member("Dora");
        browser.post("/api/session", signIn("Dora", TestAccounts.PASSWORD));
        assertThat(browser.get("/api/session"))
                .bodyJson()
                .extractingPath("$.account.permissions")
                .asArray()
                .doesNotContain("MANAGE_ROLES");

        dsl.insertInto(ACCOUNT_ROLE, ACCOUNT_ROLE.ACCOUNT_ID, ACCOUNT_ROLE.ROLE_ID)
                .values(dora, roles.idOf(BuiltInRole.ADMIN))
                .execute();

        assertThat(browser.get("/api/session"))
                .bodyJson()
                .extractingPath("$.account.permissions")
                .asArray()
                .contains("MANAGE_ROLES");
    }

    @Test
    void anAccountCanChooseItsLanguage() {
        browser.post("/api/session", signIn("Bence", TestAccounts.PASSWORD));

        assertThat(browser.put("/api/account/language", "{\"language\": \"nl\"}")).hasStatus(204);

        assertThat(browser.get("/api/session")).bodyJson().extractingPath("$.account.language").isEqualTo("nl");
    }

    private static String signIn(String username, String password) {
        return signIn(username, password, false);
    }

    private static String signIn(String username, String password, boolean staySignedIn) {
        return """
                {"username": "%s", "password": "%s", "staySignedIn": %s}
                """.formatted(username, password, staySignedIn);
    }
}
