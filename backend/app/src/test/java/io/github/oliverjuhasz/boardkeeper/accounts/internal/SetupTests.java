package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.oliverjuhasz.boardkeeper.TestBrowser;
import io.github.oliverjuhasz.boardkeeper.TestDatabase;
import io.github.oliverjuhasz.boardkeeper.TestcontainersConfiguration;
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
class SetupTests {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private TestDatabase database;

    @Autowired
    private TestAccounts accounts;

    @Autowired
    private SetupLink setupLink;

    private TestBrowser browser;

    @BeforeEach
    void startWithAnEmptyInstallation() {
        database.empty();
        browser = TestBrowser.open(mvc);
    }

    @Test
    void setupIsNeededUntilTheFirstAccountExists() {
        assertThat(browser.get("/api/setup")).hasStatusOk().bodyJson().isLenientlyEqualTo("{\"required\": true}");

        accounts.admin("anna");

        assertThat(browser.get("/api/setup")).hasStatusOk().bodyJson().isLenientlyEqualTo("{\"required\": false}");
    }

    @Test
    void theLinkFromTheLogCreatesTheFirstAdminAndSignsIn() {
        String token = setupLink.offer();

        assertThat(browser.post("/api/setup", setupRequest(token)))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"account": {"username": "anna", "playerName": "Anna", "language": "hu"}}
                        """);

        // The same browser is now signed in, as an Admin: an Admin holds every Permission.
        assertThat(browser.get("/api/session"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.account.permissions")
                .asArray()
                .contains("MANAGE_ROLES", "MANAGE_ACCOUNTS", "CREATE_PLAYS");
    }

    @Test
    void aWrongTokenIsRefused() {
        setupLink.offer();

        assertThat(browser.post("/api/setup", setupRequest("not-the-token")))
                .hasStatus(403)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"setup.wrongLink\"}");
        assertThat(browser.get("/api/setup")).bodyJson().isLenientlyEqualTo("{\"required\": true}");
    }

    @Test
    void theLinkWorksOnlyOnce() {
        String token = setupLink.offer();
        browser.post("/api/setup", setupRequest(token));

        TestBrowser someoneElse = TestBrowser.open(mvc);
        assertThat(someoneElse.post("/api/setup", setupRequest(token).replace("anna", "bence")))
                .hasStatus(409)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"setup.alreadyDone\"}");
    }

    @Test
    void aPasswordThatIsTooShortIsRefused() {
        String token = setupLink.offer();
        String request = setupRequest(token).replace(TestAccounts.PASSWORD, "fifteen chars..");

        assertThat(browser.post("/api/setup", request))
                .hasStatus(400)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"code": "request.invalid", "errors": [{"field": "password", "code": "size"}]}
                        """);
        // The link was not used up by the failed attempt.
        assertThat(browser.post("/api/setup", setupRequest(token))).hasStatusOk();
    }

    private static String setupRequest(String token) {
        return """
                {"token": "%s", "username": "anna", "password": "%s", "playerName": "Anna", "language": "hu"}
                """.formatted(token, TestAccounts.PASSWORD);
    }
}
