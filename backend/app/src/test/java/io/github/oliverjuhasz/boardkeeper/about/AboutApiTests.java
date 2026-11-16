package io.github.oliverjuhasz.boardkeeper.about;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.oliverjuhasz.boardkeeper.TestBrowser;
import io.github.oliverjuhasz.boardkeeper.TestDatabase;
import io.github.oliverjuhasz.boardkeeper.TestcontainersConfiguration;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.TestAccounts;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AboutApiTests {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private TestDatabase database;

    @Autowired
    private TestAccounts accounts;

    @Test
    void tellsWhereTheSourceCodeIs() {
        database.empty();
        accounts.member("bence");

        var about = assertThat(accounts.signedIn(mvc, "bence").get("/api/about"))
                .hasStatusOk()
                .bodyJson();
        about.extractingPath("$.repository").isEqualTo("https://github.com/oliverJuhasz/board-keeper");
        about.extractingPath("$.sourceCode")
                .asString()
                .startsWith("https://github.com/oliverJuhasz/board-keeper/tree/");
    }

    /**
     * The tests run on a build made from this repository, so git knew its commit. This fails when
     * Spring Boot can no longer read the time as the build writes it into git.properties.
     */
    @Test
    void tellsWhichCommitIsRunning() {
        Assumptions.assumeTrue(new ClassPathResource("git.properties").exists(), "built from a copy without git");
        database.empty();
        accounts.member("bence");

        var about = assertThat(accounts.signedIn(mvc, "bence").get("/api/about"))
                .hasStatusOk()
                .bodyJson();
        about.extractingPath("$.commit").asString().matches("[0-9a-f]{40}");
        about.extractingPath("$.committedAt").asString().matches("\\d{4}-\\d{2}-\\d{2}T.*Z");
        about.extractingPath("$.uncommittedChanges").isInstanceOf(Boolean.class);
    }

    @Test
    void isForSignedInAccountsOnly() {
        assertThat(TestBrowser.open(mvc).get("/api/about")).hasStatus(401);
    }
}
