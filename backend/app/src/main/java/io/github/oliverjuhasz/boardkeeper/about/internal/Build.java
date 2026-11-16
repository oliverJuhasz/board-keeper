package io.github.oliverjuhasz.boardkeeper.about.internal;

import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.regex.Pattern;
import org.springframework.boot.info.GitProperties;

/**
 * Which version of Board Keeper is running, as git knew it when the application was built. No file
 * carries the version: a release is a git tag such as {@code 1.2.3}, and anything else is a Snapshot
 * (ADR 0018).
 *
 * @param commit the commit the build was made from; {@code null} when it was made from a copy without git
 * @param committedAt when that commit was made
 * @param uncommittedChanges whether files were changed after that commit, as while developing
 * @param version the release this is; {@code null} for a Snapshot
 * @param latestRelease for a Snapshot, the newest release before its commit; {@code null} when there is none
 * @param changesSinceRelease for a Snapshot, how many commits it is past {@code latestRelease}
 */
record Build(
        String commit,
        Instant committedAt,
        boolean uncommittedChanges,
        String version,
        String latestRelease,
        Integer changesSinceRelease) {

    /** A release's tag: three numbers, and nothing before or after them. */
    private static final Pattern RELEASE = Pattern.compile("\\d{1,9}\\.\\d{1,9}\\.\\d{1,9}");

    /**
     * @param git what {@code git-commit-id-maven-plugin} wrote into {@code git.properties} (see the
     *     plugin in {@code pom.xml} for what each entry is); {@code null} when the build had no git
     */
    static Build of(GitProperties git) {
        if (git == null || git.getCommitId() == null) {
            return new Build(null, null, false, null, null, null);
        }
        boolean uncommittedChanges = Boolean.parseBoolean(git.get("dirty"));
        String tagged = newestRelease(git.get("tags"));
        // Code that was changed after the tag is no longer that release.
        if (tagged != null && !uncommittedChanges) {
            return new Build(git.getCommitId(), git.getCommitTime(), false, tagged, null, null);
        }
        String latest = git.get("closest.tag.name");
        if (latest == null || !RELEASE.matcher(latest).matches()) {
            return new Build(git.getCommitId(), git.getCommitTime(), uncommittedChanges, null, null, null);
        }
        return new Build(
                git.getCommitId(),
                git.getCommitTime(),
                uncommittedChanges,
                null,
                latest,
                Integer.valueOf(git.get("closest.tag.commit.count")));
    }

    /** The highest release among a commit's tags, which git lists separated by commas. */
    private static String newestRelease(String tags) {
        if (tags == null) {
            return null;
        }
        return Arrays.stream(tags.split(","))
                .map(String::strip)
                .filter(tag -> RELEASE.matcher(tag).matches())
                .max(Comparator.comparing(Build::numbers, Arrays::compare))
                .orElse(null);
    }

    /** {@code 1.10.0} as 1, 10 and 0, so that it counts as newer than {@code 1.9.0}. */
    private static int[] numbers(String release) {
        return Arrays.stream(release.split("\\.")).mapToInt(Integer::parseInt).toArray();
    }
}
