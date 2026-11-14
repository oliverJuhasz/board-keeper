package io.github.oliverjuhasz.boardkeeper.about.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.GitProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tells the frontend where the source code of the running version is. The AGPL asks whoever runs a changed
 * copy for others to offer them its source (section 13); the link in the account menu makes that offer.
 */
@RestController
@RequestMapping("/api/about")
@Tag(name = "About")
class AboutController {

    private final String sourceCode;

    /**
     * @param address the {@code boardkeeper.source.url} setting, where {@code {commit}} stands for the commit
     * @param git what the build recorded about its commit in {@code git.properties}, if anything
     */
    AboutController(@Value("${boardkeeper.source.url}") String address, ObjectProvider<GitProperties> git) {
        this.sourceCode = sourceCodeOf(address, git.getIfAvailable());
    }

    @GetMapping
    @Operation(
            summary = "About the running version",
            description = "Where the source code of the running version is. Signed-in Accounts only, because the"
                    + " exact version is of no use to anyone else.",
            operationId = "getAbout")
    AboutResponse about() {
        return new AboutResponse(sourceCode);
    }

    /** The address with the build's commit filled in; without {@code git}, the newest code. */
    static String sourceCodeOf(String address, GitProperties git) {
        String commit = git == null ? null : git.getCommitId();
        return address.replace("{commit}", commit == null ? "HEAD" : commit);
    }

    @Schema(name = "About", requiredProperties = "sourceCode")
    record AboutResponse(@Schema(description = "Where the source code of the running version is") String sourceCode) {}
}
