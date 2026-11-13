package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.oliverjuhasz.boardkeeper.TestBrowser;
import io.github.oliverjuhasz.boardkeeper.TestDatabase;
import io.github.oliverjuhasz.boardkeeper.TestcontainersConfiguration;
import io.github.oliverjuhasz.boardkeeper.problems.Problem;
import java.io.UnsupportedEncodingException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.webauthn.management.MapPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.MapUserCredentialRepository;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Passkeys, with a pretend phone ({@link TestPhone}) that signs with real keys. The tests run
 * with Board Keeper's address set to Vite's, as while developing (see config/application.properties
 * in the test resources).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PasskeyTests {

    private static final String ADDRESS = "http://localhost:5173";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private TestDatabase database;

    @Autowired
    private TestAccounts accounts;

    private UUID anna;
    private TestBrowser annasBrowser;
    private TestPhone annasPhone;

    @BeforeEach
    void startWithAnAdminAndAMember() {
        database.empty();
        anna = accounts.admin("anna");
        accounts.member("bence");
        annasBrowser = accounts.signedIn(mvc, "anna");
        annasPhone = new TestPhone(ADDRESS);
    }

    @Test
    void theSessionSaysThatPasskeysAreOffered() {
        assertThat(TestBrowser.open(mvc).get("/api/session")).bodyJson().extractingPath("$.passkeys").isEqualTo(true);
    }

    @Test
    void aPasskeyIsAddedAndThenSignsIn() {
        assertThat(addPasskey(annasBrowser, annasPhone, "My phone"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"label\": \"My phone\"}");
        assertThat(annasBrowser.get("/api/account/passkeys"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("[{\"label\": \"My phone\"}]");
        assertThat(annasBrowser.get("/api/account/passkeys")).bodyJson().doesNotHavePath("$[0].lastUsed");

        TestBrowser elsewhere = TestBrowser.open(mvc);
        assertThat(signIn(elsewhere, annasPhone))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"account\": {\"username\": \"anna\"}, \"passkeys\": true}");
        assertThat(elsewhere.get("/api/session")).bodyJson().extractingPath("$.account.username").isEqualTo("anna");
        assertThat(annasBrowser.get("/api/account/passkeys")).bodyJson().hasPath("$[0].lastUsed");
    }

    @Test
    void whatThePhoneSignedWorksOnlyOnce() {
        addPasskey(annasBrowser, annasPhone, "My phone");
        TestBrowser elsewhere = TestBrowser.open(mvc);
        String signed = annasPhone.get(body(elsewhere.post("/api/session/passkey/options", "{}")));
        assertThat(elsewhere.post("/api/session/passkey", signInRequest(signed))).hasStatusOk();

        TestBrowser someoneElse = TestBrowser.open(mvc);
        someoneElse.post("/api/session/passkey/options", "{}");
        assertThat(someoneElse.post("/api/session/passkey", signInRequest(signed)))
                .hasStatus(401)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"session.passkeyNotAccepted\"}");
    }

    @Test
    void signingInNeedsOptionsFromTheServerFirst() {
        addPasskey(annasBrowser, annasPhone, "My phone");
        TestBrowser elsewhere = TestBrowser.open(mvc);
        String signed = annasPhone.get(body(TestBrowser.open(mvc).post("/api/session/passkey/options", "{}")));

        assertThat(elsewhere.post("/api/session/passkey", signInRequest(signed)))
                .hasStatus(401)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"session.passkeyNotAccepted\"}");
    }

    @Test
    void aDisabledAccountCannotSignInWithAPasskey() {
        addPasskey(annasBrowser, annasPhone, "My phone");
        accounts.disable(anna);

        assertThat(signIn(TestBrowser.open(mvc), annasPhone))
                .hasStatus(401)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"session.accountDisabled\"}");
    }

    @Test
    void aPasskeyMadeForAnotherAddressIsRefused() {
        assertThat(addPasskey(annasBrowser, new TestPhone("https://not-board-keeper.example"), "Phishing"))
                .hasStatus(422)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"passkeys.notAdded\"}");
        assertThat(annasBrowser.get("/api/account/passkeys")).bodyJson().isLenientlyEqualTo("[]");
    }

    @Test
    void optionsAreForTheAccountThatAskedForThem() {
        String annasOptions = body(annasBrowser.post("/api/account/passkeys/options", "{}"));
        TestBrowser bencesBrowser = accounts.signedIn(mvc, "bence");

        assertThat(bencesBrowser.post("/api/account/passkeys", addRequest("Not mine", annasPhone.create(annasOptions))))
                .hasStatus(422)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"passkeys.notAdded\"}");
    }

    @Test
    void aPasskeyNeedsAName() {
        String options = body(annasBrowser.post("/api/account/passkeys/options", "{}"));

        assertThat(annasBrowser.post("/api/account/passkeys", addRequest(" ", annasPhone.create(options))))
                .hasStatus(400);
    }

    @Test
    void onlyItsOwnerRemovesAPasskey() {
        String id = idOf(addPasskey(annasBrowser, annasPhone, "My phone"));

        assertThat(accounts.signedIn(mvc, "bence").delete("/api/account/passkeys/" + id))
                .hasStatus(404)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"passkeys.notFound\"}");
        assertThat(annasBrowser.delete("/api/account/passkeys/" + id)).hasStatus(204);
        assertThat(annasBrowser.get("/api/account/passkeys")).bodyJson().isLenientlyEqualTo("[]");
        // A removed passkey opens nothing any more.
        assertThat(signIn(TestBrowser.open(mvc), annasPhone)).hasStatus(401);
    }

    @Test
    void managingPasskeysNeedsASignIn() {
        assertThat(TestBrowser.open(mvc).get("/api/account/passkeys")).hasStatus(401);
        assertThat(TestBrowser.open(mvc).post("/api/account/passkeys/options", "{}")).hasStatus(401);
    }

    @Test
    void withoutBoardKeepersAddressPasskeysAreOff() {
        Passkeys off = new Passkeys("", null, null, null);

        assertThat(off.available()).isFalse();
        assertThatThrownBy(() -> off.startSignIn(null, null))
                .isInstanceOf(Problem.class)
                .hasFieldOrPropertyWithValue("code", "passkeys.unavailable");
    }

    @Test
    void theAddressMustBeHttpsExceptOnThisComputer() {
        assertThatThrownBy(() -> passkeysAt("http://boardkeeper.example.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOARDKEEPER_URL");
        assertThat(passkeysAt("https://boardkeeper.example.com").available()).isTrue();
        assertThat(passkeysAt("http://localhost:5173").available()).isTrue();
    }

    /** Passkeys for an address, kept in memory instead of in the database. */
    private static Passkeys passkeysAt(String address) {
        return new Passkeys(
                address, new MapPublicKeyCredentialUserEntityRepository(), new MapUserCredentialRepository(), null);
    }

    private MvcTestResult addPasskey(TestBrowser browser, TestPhone phone, String label) {
        String options = body(browser.post("/api/account/passkeys/options", "{}"));
        return browser.post("/api/account/passkeys", addRequest(label, phone.create(options)));
    }

    private MvcTestResult signIn(TestBrowser browser, TestPhone phone) {
        String options = body(browser.post("/api/session/passkey/options", "{}"));
        return browser.post("/api/session/passkey", signInRequest(phone.get(options)));
    }

    private static String addRequest(String label, String credential) {
        return "{\"label\": \"%s\", \"credential\": %s}".formatted(label, credential);
    }

    private static String signInRequest(String credential) {
        return "{\"credential\": %s, \"staySignedIn\": false}".formatted(credential);
    }

    private static String idOf(MvcTestResult result) {
        return body(result).replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
