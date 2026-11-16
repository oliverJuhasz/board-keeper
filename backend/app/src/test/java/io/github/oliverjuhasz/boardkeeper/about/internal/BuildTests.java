package io.github.oliverjuhasz.boardkeeper.about.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.GitProperties;

class BuildTests {

    private static final String COMMIT = "1221b004748ae4f76d42d91dcdba7abb5890b2b7";

    @Test
    void aBuildFromAReleasesTagIsThatRelease() {
        Build build = Build.of(git("1.2.3", "false", "1.2.3", "0"));

        assertThat(build.version()).isEqualTo("1.2.3");
        assertThat(build.commit()).isEqualTo(COMMIT);
        assertThat(build.committedAt()).isEqualTo(Instant.parse("2026-11-14T22:40:52Z"));
        assertThat(build.uncommittedChanges()).isFalse();
        assertThat(build.latestRelease()).isNull();
        assertThat(build.changesSinceRelease()).isNull();
    }

    @Test
    void aBuildFromAnyOtherCommitIsASnapshotPastTheNewestRelease() {
        Build build = Build.of(git("", "false", "1.2.3", "4"));

        assertThat(build.version()).isNull();
        assertThat(build.commit()).isEqualTo(COMMIT);
        assertThat(build.latestRelease()).isEqualTo("1.2.3");
        assertThat(build.changesSinceRelease()).isEqualTo(4);
    }

    @Test
    void aSnapshotBeforeTheFirstReleaseHasNoReleaseBehindIt() {
        Build build = Build.of(git("", "false", "", ""));

        assertThat(build.version()).isNull();
        assertThat(build.latestRelease()).isNull();
        assertThat(build.changesSinceRelease()).isNull();
    }

    /** As while developing: the files are no longer those of the tag. */
    @Test
    void codeChangedAfterTheTagIsNoLongerThatRelease() {
        Build build = Build.of(git("1.2.3", "true", "1.2.3", "0"));

        assertThat(build.version()).isNull();
        assertThat(build.uncommittedChanges()).isTrue();
        assertThat(build.latestRelease()).isEqualTo("1.2.3");
        assertThat(build.changesSinceRelease()).isZero();
    }

    @Test
    void onlyATagOfThreeNumbersIsARelease() {
        assertThat(Build.of(git("v1.2.3,nightly,1.2,1.2.3-rc.1", "false", "", "")).version())
                .isNull();
        assertThat(Build.of(git("", "false", "1.2.3-rc.1", "2")).latestRelease()).isNull();
    }

    @Test
    void theHighestOfSeveralReleasesOnOneCommitCounts() {
        assertThat(Build.of(git("1.9.0,1.10.0,other", "false", "1.9.0", "0")).version())
                .isEqualTo("1.10.0");
    }

    /** A build made from a copy without git, such as a downloaded archive. */
    @Test
    void aBuildWithoutGitKnowsNothing() {
        assertThat(Build.of(null)).isEqualTo(new Build(null, null, false, null, null, null));
        assertThat(Build.of(new GitProperties(new Properties())))
                .isEqualTo(new Build(null, null, false, null, null, null));
    }

    /** git.properties as the build writes it; Spring Boot leaves the "git." off each name. */
    private static GitProperties git(String tags, String dirty, String closestTag, String commitsSinceClosestTag) {
        Properties entries = new Properties();
        entries.setProperty("commit.id", COMMIT);
        entries.setProperty("commit.time", "2026-11-14T23:40:52+01:00");
        entries.setProperty("dirty", dirty);
        entries.setProperty("tags", tags);
        entries.setProperty("closest.tag.name", closestTag);
        entries.setProperty("closest.tag.commit.count", commitsSinceClosestTag);
        return new GitProperties(entries);
    }
}
