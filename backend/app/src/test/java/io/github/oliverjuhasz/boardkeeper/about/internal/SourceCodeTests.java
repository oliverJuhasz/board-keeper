package io.github.oliverjuhasz.boardkeeper.about.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SourceCodeTests {

    private static final String COMMIT = "1221b004748ae4f76d42d91dcdba7abb5890b2b7";

    private static final Build RELEASE = new Build(COMMIT, null, false, "1.2.3", null, null);
    private static final Build SNAPSHOT = new Build(COMMIT, null, false, null, "1.2.3", 4);
    private static final Build WITHOUT_GIT = new Build(null, null, false, null, null, null);

    private final SourceCode source = new SourceCode(
            "https://example.org/board-keeper",
            "https://example.org/board-keeper/tree/{commit}",
            "https://example.org/board-keeper/releases/{version}");

    @Test
    void pointsToTheCommitTheBuildWasMadeFrom() {
        assertThat(source.of(RELEASE)).isEqualTo("https://example.org/board-keeper/tree/" + COMMIT);
        assertThat(source.of(SNAPSHOT)).isEqualTo("https://example.org/board-keeper/tree/" + COMMIT);
    }

    @Test
    void pointsToTheNewestCodeWithoutACommit() {
        assertThat(source.of(WITHOUT_GIT)).isEqualTo("https://example.org/board-keeper/tree/HEAD");
    }

    @Test
    void onlyAReleaseHasAPageAboutWhatIsNew() {
        assertThat(source.releaseNotesOf(RELEASE)).isEqualTo("https://example.org/board-keeper/releases/1.2.3");
        assertThat(source.releaseNotesOf(SNAPSHOT)).isNull();
        assertThat(source.releaseNotesOf(WITHOUT_GIT)).isNull();
    }

    /** A changed copy may have no page for its repository or its releases. */
    @Test
    void aSettingLeftEmptyGivesNoLink() {
        SourceCode bare = new SourceCode(" ", "https://example.org/board-keeper/tree/{commit}", "");

        assertThat(bare.repositoryPage()).isNull();
        assertThat(bare.releaseNotesOf(RELEASE)).isNull();
        assertThat(source.repositoryPage()).isEqualTo("https://example.org/board-keeper");
    }
}
