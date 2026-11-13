package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.PASSWORD_RESET;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.oliverjuhasz.boardkeeper.BoardKeeperApplication;
import io.github.oliverjuhasz.boardkeeper.TestBrowser;
import io.github.oliverjuhasz.boardkeeper.TestDatabase;
import io.github.oliverjuhasz.boardkeeper.TestcontainersConfiguration;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * The command for an installation whose only Admin has forgotten their password. The tests run
 * the application as a command does, with the command profile, and call the command after the
 * Accounts it is about exist.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(BoardKeeperApplication.COMMAND_PROFILE)
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class ResetLinkCommandTests {

    private static final Pattern LINK = Pattern.compile("/reset-password/(\\S+)");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private TestDatabase database;

    @Autowired
    private TestAccounts accounts;

    @Autowired
    private ResetLinkCommand command;

    @Autowired
    private DSLContext dsl;

    private UUID anna;

    @BeforeEach
    void startWithAnAdmin() {
        database.empty();
        anna = accounts.admin("anna");
    }

    @Test
    void theApplicationKnowsTheCommandsOption() {
        assertThat(BoardKeeperApplication.COMMAND_OPTIONS).contains(ResetLinkCommand.OPTION);
    }

    @Test
    void writesAResetLinkThatOpensTheAccount(CapturedOutput output) {
        run("--reset-link-for=Anna");

        assertThat(command.getExitCode()).isZero();
        Matcher link = LINK.matcher(output.getOut());
        assertThat(link.find()).as("a link in the log").isTrue();
        assertThat(output.getOut()).containsPattern("until \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2} UTC");
        // Nobody created it in the app.
        assertThat(dsl.select(PASSWORD_RESET.CREATED_BY)
                        .from(PASSWORD_RESET)
                        .where(PASSWORD_RESET.ACCOUNT_ID.eq(anna))
                        .fetchSingle()
                        .value1())
                .isNull();

        TestBrowser browser = TestBrowser.open(mvc);
        assertThat(browser.get("/api/password-reset/" + link.group(1)))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"username\": \"anna\"}");
        assertThat(browser.post(
                        "/api/password-reset/" + link.group(1), "{\"password\": \"a brand new long password\"}"))
                .hasStatusOk();
        accounts.signedIn(mvc, "anna", "a brand new long password");
    }

    @Test
    void saysSoWhenThereIsNoSuchAccount(CapturedOutput output) {
        run("--reset-link-for=bence");

        assertThat(command.getExitCode()).isEqualTo(1);
        assertThat(output.getOut()).contains("There is no Account with the username \"bence\".");
        assertThat(dsl.fetchCount(PASSWORD_RESET)).isZero();
    }

    @Test
    void makesNoLinkForADisabledAccount(CapturedOutput output) {
        accounts.disable(anna);

        run("--reset-link-for=anna");

        assertThat(command.getExitCode()).isEqualTo(1);
        assertThat(output.getOut()).contains("The Account \"anna\" is disabled.");
        assertThat(dsl.fetchCount(PASSWORD_RESET)).isZero();
    }

    @Test
    void needsAUsername(CapturedOutput output) {
        run("--reset-link-for");

        assertThat(command.getExitCode()).isEqualTo(1);
        assertThat(output.getOut()).contains("Give one username: --reset-link-for=USERNAME");
    }

    private void run(String... args) {
        command.run(new DefaultApplicationArguments(args));
    }
}
