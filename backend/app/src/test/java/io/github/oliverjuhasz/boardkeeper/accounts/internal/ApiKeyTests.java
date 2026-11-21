package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.API_KEY;
import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.ROLE_PERMISSION;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.oliverjuhasz.boardkeeper.TestBrowser;
import io.github.oliverjuhasz.boardkeeper.TestDatabase;
import io.github.oliverjuhasz.boardkeeper.TestcontainersConfiguration;
import io.github.oliverjuhasz.boardkeeper.access.Permission;
import java.io.UnsupportedEncodingException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * API Keys (ADR 0017): creating them, and what a request with one may and may not do. Requests
 * with an API Key are sent the way a script sends them: no cookies, no CSRF token, only the
 * {@code Authorization} header.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ApiKeyTests {

    private static final String ADDRESS = "http://localhost:5173";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private TestDatabase database;

    @Autowired
    private TestAccounts accounts;

    @Autowired
    private DSLContext dsl;

    private UUID anna;
    private TestBrowser annasBrowser;

    @BeforeEach
    void startWithAnAdmin() {
        database.empty();
        anna = accounts.admin("anna");
        annasBrowser = accounts.signedIn(mvc, "anna");
    }

    @Test
    void anApiKeyIsShownOnceAndThenListed() {
        MvcTestResult created = annasBrowser.post("/api/account/api-keys", withPassword("Backup script", "NINETY_DAYS"));

        assertThat(created).hasStatusOk().bodyJson().isLenientlyEqualTo("{\"apiKey\": {\"name\": \"Backup script\"}}");
        assertThat(secretOf(created)).startsWith("bk_").hasSizeGreaterThan(40);
        assertThat(created).bodyJson().hasPath("$.apiKey.expires").doesNotHavePath("$.apiKey.lastUsed");

        MvcTestResult listed = annasBrowser.get("/api/account/api-keys");
        assertThat(listed).hasStatusOk().bodyJson().isLenientlyEqualTo("[{\"name\": \"Backup script\"}]");
        assertThat(body(listed)).doesNotContain(secretOf(created));
        assertThat(dsl.fetchValue(API_KEY.SECRET_HASH)).isNotEqualTo(secretOf(created));
    }

    @Test
    void anApiKeyCanBeMadeToNeverExpire() {
        assertThat(annasBrowser.post("/api/account/api-keys", withPassword("Forever", "NEVER")))
                .hasStatusOk()
                .bodyJson()
                .doesNotHavePath("$.apiKey.expires");
    }

    @Test
    void anApiKeyActsAsItsAccountWithoutCookiesOrCsrfToken() {
        String apiKey = createApiKey(annasBrowser, "Script");

        MvcTestResult added = withApiKey(apiKey, HttpMethod.POST, "/api/players", "{\"name\": \"Zsófi\"}");
        assertThat(added).hasStatus2xxSuccessful();
        // No session is made for it.
        assertThat(added.getResponse().getCookie("SESSION")).isNull();

        MvcTestResult session = withApiKey(apiKey, HttpMethod.GET, "/api/session", null);
        assertThat(session).hasStatusOk().bodyJson().extractingPath("$.account.username").isEqualTo("anna");
        assertThat(session.getResponse().getCookie("SESSION")).isNull();
        assertThat(annasBrowser.get("/api/account/api-keys")).bodyJson().hasPath("$[0].lastUsed");
    }

    @Test
    void anAdminsApiKeyDoesNoAdminWork() {
        String apiKey = createApiKey(annasBrowser, "Script");

        assertThat(withApiKey(apiKey, HttpMethod.GET, "/api/session", null))
                .bodyJson()
                .extractingPath("$.account.permissions")
                .asArray()
                .contains(Permission.CREATE_PLAYERS.name(), Permission.USE_API_KEYS.name())
                .doesNotContain(
                        Permission.MANAGE_ACCOUNTS.name(),
                        Permission.MANAGE_INVITES.name(),
                        Permission.MANAGE_ROLES.name(),
                        Permission.RECALCULATE_PLAYS.name(),
                        Permission.EXPORT.name(),
                        Permission.IMPORT.name());
        assertDeniedToApiKey(withApiKey(apiKey, HttpMethod.GET, "/api/accounts", null));
        assertDeniedToApiKey(withApiKey(apiKey, HttpMethod.GET, "/api/export", null));
        assertDeniedToApiKey(withApiKey(apiKey, HttpMethod.POST, "/api/import/review", "{\"format\": 1}"));
        UUID player = accounts.playerOf(accounts.member("bence"));
        assertDeniedToApiKey(
                withApiKey(apiKey, HttpMethod.POST, "/api/reset-links", "{\"playerId\": \"%s\"}".formatted(player)));
    }

    @Test
    void anApiKeyCannotSignInOrChangeHowItsAccountSignsIn() {
        String apiKey = createApiKey(annasBrowser, "Script");

        assertDeniedToApiKey(withApiKey(apiKey, HttpMethod.POST, "/api/session", TestAccounts.signInRequest("anna", TestAccounts.PASSWORD)));
        assertDeniedToApiKey(withApiKey(apiKey, HttpMethod.DELETE, "/api/session", null));
        assertDeniedToApiKey(withApiKey(apiKey, HttpMethod.POST, "/api/session/passkey/options", "{}"));
        assertDeniedToApiKey(withApiKey(
                apiKey,
                HttpMethod.PUT,
                "/api/account/password",
                "{\"currentPassword\": \"%s\", \"newPassword\": \"a brand new long password\"}"
                        .formatted(TestAccounts.PASSWORD)));
        assertDeniedToApiKey(withApiKey(apiKey, HttpMethod.GET, "/api/account/passkeys", null));
        assertDeniedToApiKey(withApiKey(apiKey, HttpMethod.POST, "/api/account/passkeys/options", "{}"));
        assertDeniedToApiKey(withApiKey(apiKey, HttpMethod.GET, "/api/account/api-keys", null));
        assertDeniedToApiKey(withApiKey(apiKey, HttpMethod.POST, "/api/account/api-keys", withPassword("Another", "NEVER")));
        assertDeniedToApiKey(withApiKey(apiKey, HttpMethod.DELETE, "/api/account/api-keys/" + UUID.randomUUID(), null));
        assertDeniedToApiKey(withApiKey(apiKey, HttpMethod.POST, "/api/registration/some-token", "{}"));
        assertDeniedToApiKey(withApiKey(apiKey, HttpMethod.POST, "/api/password-reset/some-token", "{}"));
    }

    @Test
    void theApiKeyCountsEvenWithSomeoneElsesSessionCookie() {
        String annasApiKey = createApiKey(annasBrowser, "Script");
        accounts.member("bence");
        TestBrowser bencesBrowser = accounts.signedIn(mvc, "bence");

        var request = mvc.get()
                .uri("/api/session")
                .cookie(bencesBrowser.cookie("SESSION"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + annasApiKey);
        assertThat(request.exchange()).bodyJson().extractingPath("$.account.username").isEqualTo("anna");
    }

    @Test
    void aWrongApiKeyIsRefusedEvenWithASession() {
        var request = mvc.get()
                .uri("/api/session")
                .cookie(annasBrowser.cookie("SESSION"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer bk_not-an-api-key");

        MvcTestResult refused = request.exchange();
        assertThat(refused).hasStatus(401).bodyJson().isLenientlyEqualTo("{\"code\": \"access.apiKeyNotValid\"}");
        assertThat(refused.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
    }

    @Test
    void otherAuthorizationHeadersAreLeftAlone() {
        // As a reverse proxy in front of Board Keeper might send. The session still counts, and
        // so does the CSRF check.
        var signedIn = mvc.get()
                .uri("/api/session")
                .cookie(annasBrowser.cookie("SESSION"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer some-proxys-token");
        assertThat(signedIn.exchange()).bodyJson().extractingPath("$.account.username").isEqualTo("anna");

        var forged = mvc.post()
                .uri("/api/players")
                .cookie(annasBrowser.cookie("SESSION"))
                .header(HttpHeaders.AUTHORIZATION, "Basic YW5uYTpwYXNzd29yZA==")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Forged\"}");
        assertThat(forged.exchange()).hasStatus(403);
    }

    @Test
    void anExpiredApiKeyIsRefused() {
        String apiKey = createApiKey(annasBrowser, "Old script");
        dsl.update(API_KEY).set(API_KEY.EXPIRES_AT, OffsetDateTime.now().minusMinutes(1)).execute();

        assertThat(withApiKey(apiKey, HttpMethod.GET, "/api/players", null))
                .hasStatus(401)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"access.apiKeyExpired\"}");
    }

    @Test
    void creatingAnApiKeyNeedsThePermission() {
        accounts.member("bence");
        TestBrowser bencesBrowser = accounts.signedIn(mvc, "bence");

        assertThat(bencesBrowser.post("/api/account/api-keys", withPassword("Script", "NEVER")))
                .hasStatus(403)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"access.denied\"}");
        assertThat(bencesBrowser.post("/api/account/api-keys/passkey-options", "{}")).hasStatus(403);
    }

    @Test
    void withoutThePermissionTheApiKeysStopButCanStillBeDeleted() {
        accounts.memberWith("cili", Permission.USE_API_KEYS, Permission.CREATE_PLAYERS);
        TestBrowser cilisBrowser = accounts.signedIn(mvc, "cili");
        String apiKey = createApiKey(cilisBrowser, "Script");
        assertThat(withApiKey(apiKey, HttpMethod.GET, "/api/players", null)).hasStatusOk();

        dsl.deleteFrom(ROLE_PERMISSION)
                .where(ROLE_PERMISSION.PERMISSION.eq(Permission.USE_API_KEYS.name()))
                .execute();

        assertThat(withApiKey(apiKey, HttpMethod.GET, "/api/players", null))
                .hasStatus(403)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"access.apiKeysNotAllowed\"}");
        String id = idOf(cilisBrowser.get("/api/account/api-keys"));
        assertThat(cilisBrowser.delete("/api/account/api-keys/" + id)).hasStatus(204);
        assertThat(cilisBrowser.get("/api/account/api-keys")).bodyJson().isLenientlyEqualTo("[]");
    }

    @Test
    void disablingAnAccountDeletesItsApiKeys() {
        UUID bence = accounts.memberWith("bence", Permission.USE_API_KEYS);
        String apiKey = createApiKey(accounts.signedIn(mvc, "bence"), "Script");

        assertThat(annasBrowser.put("/api/accounts/%s/disabled".formatted(bence), "{\"disabled\": true}"))
                .hasStatus(204);
        assertThat(annasBrowser.put("/api/accounts/%s/disabled".formatted(bence), "{\"disabled\": false}"))
                .hasStatus(204);

        assertThat(withApiKey(apiKey, HttpMethod.GET, "/api/players", null))
                .hasStatus(401)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"access.apiKeyNotValid\"}");
        assertThat(dsl.fetchCount(API_KEY)).isZero();
    }

    @Test
    void changingThePasswordKeepsTheApiKeys() {
        String apiKey = createApiKey(annasBrowser, "Script");

        assertThat(annasBrowser.put(
                        "/api/account/password",
                        "{\"currentPassword\": \"%s\", \"newPassword\": \"a brand new long password\"}"
                                .formatted(TestAccounts.PASSWORD)))
                .hasStatus(204);

        assertThat(withApiKey(apiKey, HttpMethod.GET, "/api/players", null)).hasStatusOk();
    }

    @Test
    void creatingAnApiKeyNeedsTheRightPassword() {
        assertThat(annasBrowser.post("/api/account/api-keys", newApiKey("Script", "NEVER", "\"password\": \"not my password\"")))
                .hasStatus(403)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"accounts.wrongCurrentPassword\"}");
        assertThat(annasBrowser.get("/api/account/api-keys")).bodyJson().isLenientlyEqualTo("[]");
    }

    @Test
    void creatingAnApiKeyNeedsExactlyOneConfirmation() {
        assertThat(annasBrowser.post("/api/account/api-keys", newApiKey("Script", "NEVER", null)))
                .hasStatus(422)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"apiKeys.confirmationNeeded\"}");

        TestPhone annasPhone = new TestPhone(ADDRESS);
        addPasskey(annasBrowser, annasPhone);
        String options = body(annasBrowser.post("/api/account/api-keys/passkey-options", "{}"));
        String both = "\"password\": \"%s\", \"passkey\": %s".formatted(TestAccounts.PASSWORD, annasPhone.get(options));
        assertThat(annasBrowser.post("/api/account/api-keys", newApiKey("Script", "NEVER", both)))
                .hasStatus(422)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"apiKeys.confirmationNeeded\"}");
    }

    @Test
    void namesAreUniqueAmongAnAccountsApiKeys() {
        createApiKey(annasBrowser, "Script");

        assertThat(annasBrowser.post("/api/account/api-keys", withPassword(" script ", "NEVER")))
                .hasStatus(409)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"apiKeys.nameTaken\"}");
        // Another Account may use the same name.
        accounts.memberWith("bence", Permission.USE_API_KEYS);
        assertThat(accounts.signedIn(mvc, "bence").post("/api/account/api-keys", withPassword("Script", "NEVER")))
                .hasStatusOk();
    }

    @Test
    void onlyItsOwnerDeletesAnApiKey() {
        String apiKey = createApiKey(annasBrowser, "Script");
        String id = idOf(annasBrowser.get("/api/account/api-keys"));
        accounts.memberWith("bence", Permission.USE_API_KEYS);

        assertThat(accounts.signedIn(mvc, "bence").delete("/api/account/api-keys/" + id))
                .hasStatus(404)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"apiKeys.notFound\"}");
        assertThat(annasBrowser.delete("/api/account/api-keys/" + id)).hasStatus(204);
        assertThat(withApiKey(apiKey, HttpMethod.GET, "/api/players", null)).hasStatus(401);
    }

    @Test
    void anApiKeyCanBeConfirmedWithAPasskey() {
        TestPhone annasPhone = new TestPhone(ADDRESS);
        addPasskey(annasBrowser, annasPhone);

        String options = body(annasBrowser.post("/api/account/api-keys/passkey-options", "{}"));
        MvcTestResult created = annasBrowser.post(
                "/api/account/api-keys", newApiKey("Agent", "ONE_YEAR", "\"passkey\": " + annasPhone.get(options)));

        assertThat(created).hasStatusOk().bodyJson().isLenientlyEqualTo("{\"apiKey\": {\"name\": \"Agent\"}}");
        assertThat(withApiKey(secretOf(created), HttpMethod.GET, "/api/players", null)).hasStatusOk();
    }

    @Test
    void onlyTheAccountsOwnPasskeyConfirms() {
        accounts.memberWith("bence", Permission.USE_API_KEYS);
        TestBrowser bencesBrowser = accounts.signedIn(mvc, "bence");
        TestPhone annasPhone = new TestPhone(ADDRESS);
        addPasskey(annasBrowser, annasPhone);

        String options = body(bencesBrowser.post("/api/account/api-keys/passkey-options", "{}"));
        assertThat(bencesBrowser.post(
                        "/api/account/api-keys", newApiKey("Agent", "NEVER", "\"passkey\": " + annasPhone.get(options))))
                .hasStatus(403)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"passkeys.notConfirmed\"}");
    }

    @Test
    void confirmingWithAPasskeyNeedsOptionsFromTheServerFirst() {
        TestPhone annasPhone = new TestPhone(ADDRESS);
        addPasskey(annasBrowser, annasPhone);
        // Options asked for to sign in don't stand in for options to confirm with.
        String signInOptions = body(annasBrowser.post("/api/session/passkey/options", "{}"));

        assertThat(annasBrowser.post(
                        "/api/account/api-keys",
                        newApiKey("Agent", "NEVER", "\"passkey\": " + annasPhone.get(signInOptions))))
                .hasStatus(403)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"passkeys.notConfirmed\"}");
    }

    private static void assertDeniedToApiKey(MvcTestResult result) {
        assertThat(result).hasStatus(403).bodyJson().isLenientlyEqualTo("{\"code\": \"access.deniedToApiKey\"}");
    }

    /** A request as a script sends it: only the API Key, no cookies and no CSRF token. */
    private MvcTestResult withApiKey(String apiKey, HttpMethod method, String uri, String json) {
        var request = mvc.method(method).uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        if (json != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return request.exchange();
    }

    private static String createApiKey(TestBrowser browser, String name) {
        MvcTestResult created = browser.post("/api/account/api-keys", withPassword(name, "NEVER"));
        assertThat(created).hasStatusOk();
        return secretOf(created);
    }

    private static void addPasskey(TestBrowser browser, TestPhone phone) {
        String options = body(browser.post("/api/account/passkeys/options", "{}"));
        assertThat(browser.post(
                        "/api/account/passkeys",
                        "{\"label\": \"My phone\", \"credential\": %s}".formatted(phone.create(options))))
                .hasStatusOk();
    }

    private static String withPassword(String name, String expiry) {
        return newApiKey(name, expiry, "\"password\": \"%s\"".formatted(TestAccounts.PASSWORD));
    }

    /** @param confirmation the JSON for the password or the passkey, or null for neither */
    private static String newApiKey(String name, String expiry, String confirmation) {
        String fields = "\"name\": \"%s\", \"expiry\": \"%s\"".formatted(name, expiry);
        return "{" + (confirmation == null ? fields : fields + ", " + confirmation) + "}";
    }

    private static String secretOf(MvcTestResult result) {
        return body(result).replaceAll(".*\"secret\":\"([^\"]+)\".*", "$1");
    }

    private static String idOf(MvcTestResult result) {
        return body(result).replaceAll(".*?\"id\":\"([^\"]+)\".*", "$1");
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
