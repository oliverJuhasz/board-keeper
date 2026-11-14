package io.github.oliverjuhasz.boardkeeper.about;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.oliverjuhasz.boardkeeper.TestBrowser;
import io.github.oliverjuhasz.boardkeeper.TestDatabase;
import io.github.oliverjuhasz.boardkeeper.TestcontainersConfiguration;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.TestAccounts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
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

        assertThat(accounts.signedIn(mvc, "bence").get("/api/about"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.sourceCode")
                .asString()
                .startsWith("https://github.com/oliverJuhasz/board-keeper/tree/");
    }

    @Test
    void isForSignedInAccountsOnly() {
        assertThat(TestBrowser.open(mvc).get("/api/about")).hasStatus(401);
    }
}
