package io.github.oliverjuhasz.boardkeeper.frontend;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.oliverjuhasz.boardkeeper.frontend.FrontendResourceConfiguration.FrontendResourceResolver;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FrontendRoutingTests {

    @ParameterizedTest
    @ValueSource(strings = {"", "games", "games/42", "plays/2026/new"})
    void pagesOfTheFrontendGetIndexHtml(String path) {
        assertThat(FrontendResourceResolver.isFrontendRoute(path)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"api", "api/games", "actuator/health", "v3/api-docs", "swagger-ui/index.html"})
    void addressesOutsideTheFrontendDoNot(String path) {
        assertThat(FrontendResourceResolver.isFrontendRoute(path)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing.js", "assets/index-abc123.css", "favicon.ico"})
    void missingFilesDoNot(String path) {
        assertThat(FrontendResourceResolver.isFrontendRoute(path)).isFalse();
    }
}
