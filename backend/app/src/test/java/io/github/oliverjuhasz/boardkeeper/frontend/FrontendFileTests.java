package io.github.oliverjuhasz.boardkeeper.frontend;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.oliverjuhasz.boardkeeper.TestcontainersConfiguration;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * How the frontend's files are served, by the real web server: a file's type comes from the
 * server's own list of extensions, which MockMvc doesn't use. The tests fetch a file from the
 * test resources' static/test-only folder, which stands in for the built frontend without hiding
 * any of its files.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class FrontendFileTests {

    @LocalServerPort
    private int port;

    @Test
    void theAppManifestIsServedAsAManifest() throws Exception {
        assertThat(get("/test-only/app.webmanifest").headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("application/manifest+json"));
    }

    @Test
    void aTextFileSaysItIsInUtf8() throws Exception {
        assertThat(get("/test-only/notice.txt").headers().firstValue("Content-Type"))
                .hasValue("text/plain;charset=UTF-8");
    }

    @Test
    void filesOutsideTheAssetsAreCheckedWithTheServerOnEveryUse() throws Exception {
        assertThat(get("/test-only/app.webmanifest").headers().firstValue("Cache-Control")).hasValue("no-cache");
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }
}
