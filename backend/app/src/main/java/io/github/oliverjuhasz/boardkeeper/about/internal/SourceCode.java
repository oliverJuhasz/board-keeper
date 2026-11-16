package io.github.oliverjuhasz.boardkeeper.about.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where this copy of Board Keeper keeps its source code: the {@code boardkeeper.source.*} settings.
 * The AGPL asks whoever runs a changed copy for others to offer them its source (ADR 0016), so these
 * are settings and not written into the frontend: a changed copy points them at its own code.
 *
 * <p>{@code @ConfigurationProperties} makes Spring Boot fill a record from all the settings that start
 * with a prefix, which keeps settings that belong together in one place; {@code @Value} reads one
 * setting at a time. Spring Boot finds this record through {@code @ConfigurationPropertiesScan} on
 * the application class.
 *
 * @param repository the repository's own page; blank if this copy has none
 * @param url the source code of one version, with {@code {commit}} standing for the commit
 * @param releases what is new in a release, with {@code {version}} standing for the release, such as
 *     {@code 1.2.3}; blank if this copy has no such page
 */
@ConfigurationProperties("boardkeeper.source")
record SourceCode(String repository, String url, String releases) {

    /** The repository's own page, or {@code null}. */
    String repositoryPage() {
        return blankToNull(repository);
    }

    /** Where the source code of a build is. A build that doesn't know its commit points to the newest code. */
    String of(Build build) {
        return url.replace("{commit}", build.commit() == null ? "HEAD" : build.commit());
    }

    /** Where to read what is new in a build, or {@code null}: only a release has such a page. */
    String releaseNotesOf(Build build) {
        String address = blankToNull(releases);
        return address == null || build.version() == null ? null : address.replace("{version}", build.version());
    }

    private static String blankToNull(String setting) {
        return setting == null || setting.isBlank() ? null : setting.strip();
    }
}
