package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.PASSWORD_RESET;
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
class PasswordTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String NEW_PASSWORD = "a brand new long password";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private TestDatabase database;

    @Autowired
    private TestAccounts accounts;

    @Autowired
    private Players players;

    @Autowired
    private DSLContext dsl;

    private UUID bence;
    private UUID bencesPlayer;
    private TestBrowser admin;

    @BeforeEach
    void startWithAnAdminAndAMember() {
        database.empty();
        accounts.admin("anna");
        bence = accounts.member("bence");
        bencesPlayer = accounts.playerOf(bence);
        admin = accounts.signedIn(mvc, "anna");
    }

    @Test
    void changingYourPasswordNeedsTheCurrentOne() {
        TestBrowser member = accounts.signedIn(mvc, "bence");

        assertThat(member.put("/api/account/password", change("not my password", NEW_PASSWORD)))
                .hasStatus(403)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"accounts.wrongCurrentPassword\"}");
        assertThat(canSignIn("bence", TestAccounts.PASSWORD)).isTrue();
    }

    @Test
    void aChangedPasswordReplacesTheOldOneAndSignsOutTheOtherDevices() {
        TestBrowser phone = accounts.signedIn(mvc, "bence");
        TestBrowser laptop = accounts.signedIn(mvc, "bence");

        assertThat(phone.put("/api/account/password", change(TestAccounts.PASSWORD, NEW_PASSWORD))).hasStatus(204);

        assertThat(canSignIn("bence", TestAccounts.PASSWORD)).isFalse();
        assertThat(canSignIn("bence", NEW_PASSWORD)).isTrue();
        // The device that made the change stays signed in; the other one does not.
        assertThat(phone.get("/api/players")).hasStatusOk();
        assertThat(laptop.get("/api/players")).hasStatus(401);
    }

    @Test
    void aNewPasswordNeedsSixteenCharacters() {
        TestBrowser member = accounts.signedIn(mvc, "bence");

        assertThat(member.put("/api/account/password", change(TestAccounts.PASSWORD, "fifteen chars..")))
                .hasStatus(400)
                .bodyJson()
                .isLenientlyEqualTo("{\"errors\": [{\"field\": \"newPassword\", \"code\": \"size\"}]}");
    }

    @Test
    void aResetLinkLetsSomeoneChooseANewPasswordAndSignsThemIn() {
        TestBrowser oldSession = accounts.signedIn(mvc, "bence");
        String token = resetLinkFor(admin, bencesPlayer);

        TestBrowser forgetful = TestBrowser.open(mvc);
        assertThat(forgetful.get("/api/password-reset/" + token))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"username\": \"bence\"}");
        assertThat(forgetful.post("/api/password-reset/" + token, "{\"password\": \"%s\"}".formatted(NEW_PASSWORD)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.account.username")
                .isEqualTo("bence");

        assertThat(forgetful.get("/api/players")).hasStatusOk();
        assertThat(oldSession.get("/api/players")).hasStatus(401);
        assertThat(canSignIn("bence", TestAccounts.PASSWORD)).isFalse();
        assertThat(canSignIn("bence", NEW_PASSWORD)).isTrue();
    }

    @Test
    void aResetLinkWorksOnlyOnceAndExpires() {
        String used = resetLinkFor(admin, bencesPlayer);
        TestBrowser.open(mvc).post("/api/password-reset/" + used, "{\"password\": \"%s\"}".formatted(NEW_PASSWORD));
        assertNotValid(TestBrowser.open(mvc).get("/api/password-reset/" + used));

        String expired = resetLinkFor(admin, bencesPlayer);
        dsl.update(PASSWORD_RESET).set(PASSWORD_RESET.EXPIRES_AT, OffsetDateTime.now().minusMinutes(1)).execute();
        assertNotValid(TestBrowser.open(mvc).get("/api/password-reset/" + expired));
        assertNotValid(TestBrowser.open(mvc)
                .post("/api/password-reset/" + expired, "{\"password\": \"%s\"}".formatted(NEW_PASSWORD)));
    }

    @Test
    void aNewResetLinkReplacesTheEarlierOne() {
        String first = resetLinkFor(admin, bencesPlayer);
        String second = resetLinkFor(admin, bencesPlayer);

        assertNotValid(TestBrowser.open(mvc).get("/api/password-reset/" + first));
        assertThat(TestBrowser.open(mvc).get("/api/password-reset/" + second)).hasStatusOk();
    }

    @Test
    void aResetLinkDoesNotOpenADisabledAccount() {
        String token = resetLinkFor(admin, bencesPlayer);
        accounts.disable(bence);

        assertNotValid(TestBrowser.open(mvc).get("/api/password-reset/" + token));
    }

    @Test
    void aPlayerWithoutAnAccountHasNoPasswordToReset() {
        UUID dora = players.create("Dora").id();

        assertThat(admin.post("/api/reset-links", "{\"playerId\": \"%s\"}".formatted(dora)))
                .hasStatus(404)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"accounts.notFound\"}");
    }

    @Test
    void aResetLinkCannotBeMadeForAnAccountThatHoldsMoreThanItsCreator() {
        accounts.memberWith("dora", Permission.MANAGE_INVITES);
        TestBrowser dora = accounts.signedIn(mvc, "dora");
        UUID annasPlayer = accounts.playerOf(accounts.admin("second-admin"));

        // Otherwise "manage Invites" would be the key to every Admin's Account.
        assertThat(dora.post("/api/reset-links", "{\"playerId\": \"%s\"}".formatted(annasPlayer)))
                .hasStatus(403)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"access.beyondOwnPermissions\"}");
        assertThat(dora.post("/api/reset-links", "{\"playerId\": \"%s\"}".formatted(bencesPlayer))).hasStatusOk();

        TestBrowser member = accounts.signedIn(mvc, "bence");
        assertThat(member.post("/api/reset-links", "{\"playerId\": \"%s\"}".formatted(bencesPlayer))).hasStatus(403);
    }

    private boolean canSignIn(String username, String password) {
        return TestBrowser.open(mvc)
                        .post("/api/session", TestAccounts.signInRequest(username, password))
                        .getResponse()
                        .getStatus()
                == 200;
    }

    private static String resetLinkFor(TestBrowser browser, UUID playerId) {
        MvcTestResult created = browser.post("/api/reset-links", "{\"playerId\": \"%s\"}".formatted(playerId));
        assertThat(created).hasStatusOk();
        try {
            return JSON.readTree(created.getResponse().getContentAsString()).get("token").asString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void assertNotValid(MvcTestResult result) {
        assertThat(result).hasStatus(404).bodyJson().isLenientlyEqualTo("{\"code\": \"resetLinks.notValid\"}");
    }

    private static String change(String currentPassword, String newPassword) {
        return "{\"currentPassword\": \"%s\", \"newPassword\": \"%s\"}".formatted(currentPassword, newPassword);
    }
}
