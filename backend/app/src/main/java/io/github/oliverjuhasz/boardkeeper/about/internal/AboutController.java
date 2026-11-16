package io.github.oliverjuhasz.boardkeeper.about.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.GitProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tells the frontend's About page which version of Board Keeper is running (ADR 0018) and where its
 * source code is. The AGPL asks whoever runs a changed copy for others to offer them its source
 * (section 13); the links on that page make the offer (ADR 0016).
 */
@RestController
@RequestMapping("/api/about")
@Tag(name = "About")
class AboutController {

    private final AboutResponse about;

    /**
     * @param source the {@code boardkeeper.source.*} settings
     * @param git what the build recorded in {@code git.properties}, if anything
     */
    AboutController(SourceCode source, ObjectProvider<GitProperties> git) {
        this.about = AboutResponse.of(Build.of(git.getIfAvailable()), source);
    }

    @GetMapping
    @Operation(
            summary = "About the running version",
            description = "Which version of Board Keeper is running, and where its source code is. Signed-in"
                    + " Accounts only, because the exact version is of no use to anyone else.",
            operationId = "getAbout")
    AboutResponse about() {
        return about;
    }

    @Schema(
            name = "About",
            requiredProperties = {"uncommittedChanges", "sourceCode"})
    record AboutResponse(
            @Schema(
                            description = "The release that is running, such as 1.2.3. Absent for a Snapshot: a build"
                                    + " that wasn't made from a release's tag.")
                    String version,
            @Schema(description = "The commit the running version was built from. Absent when the build had no git.")
                    String commit,
            @Schema(description = "When that commit was made") Instant committedAt,
            @Schema(description = "Whether files were changed after that commit, as in a build made while developing")
                    boolean uncommittedChanges,
            @Schema(
                            description = "For a Snapshot, the newest release before its commit. Absent when there is"
                                    + " no release yet.")
                    String latestRelease,
            @Schema(description = "For a Snapshot, how many commits it is past that release")
                    Integer changesSinceRelease,
            @Schema(description = "The page of the repository that holds the source code. Absent if this copy names none.")
                    String repository,
            @Schema(description = "Where the source code of the running version is") String sourceCode,
            @Schema(description = "Where to read what is new in the release that is running. Absent for a Snapshot.")
                    String releaseNotes) {

        static AboutResponse of(Build build, SourceCode source) {
            return new AboutResponse(
                    build.version(),
                    build.commit(),
                    build.committedAt(),
                    build.uncommittedChanges(),
                    build.latestRelease(),
                    build.changesSinceRelease(),
                    source.repositoryPage(),
                    source.of(build),
                    source.releaseNotesOf(build));
        }
    }
}
