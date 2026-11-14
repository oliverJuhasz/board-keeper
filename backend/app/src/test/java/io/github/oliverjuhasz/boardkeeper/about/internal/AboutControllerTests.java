package io.github.oliverjuhasz.boardkeeper.about.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.GitProperties;

class AboutControllerTests {

    private static final String ADDRESS = "https://example.org/board-keeper/tree/{commit}";

    @Test
    void pointsToTheCommitTheBuildWasMadeFrom() {
        Properties entries = new Properties();
        entries.setProperty("commit.id", "1221b004748ae4f76d42d91dcdba7abb5890b2b7");

        assertThat(AboutController.sourceCodeOf(ADDRESS, new GitProperties(entries)))
                .isEqualTo("https://example.org/board-keeper/tree/1221b004748ae4f76d42d91dcdba7abb5890b2b7");
    }

    /** A build made from a copy without Git doesn't know its commit. */
    @Test
    void pointsToTheNewestCodeWithoutACommit() {
        assertThat(AboutController.sourceCodeOf(ADDRESS, null)).isEqualTo("https://example.org/board-keeper/tree/HEAD");
        assertThat(AboutController.sourceCodeOf(ADDRESS, new GitProperties(new Properties())))
                .isEqualTo("https://example.org/board-keeper/tree/HEAD");
    }
}
