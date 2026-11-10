package io.github.oliverjuhasz.boardkeeper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Keeps {@code api/openapi.json} in step with the API. The frontend's TypeScript types are
 * generated from that file, so it must describe the API as it really is.
 *
 * <p>After changing the API on purpose, rewrite the file and commit it:
 * {@code ./mvnw verify -Dboardkeeper.openapi.update=true}
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiSpecTests {

    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    @Autowired
    private MockMvcTester mvc;

    @Test
    void theCommittedDescriptionMatchesTheApi() throws IOException {
        Path file = Path.of(System.getProperty("boardkeeper.openapi.file", "../../api/openapi.json"));
        String current = normalise(mvc.get().uri("/v3/api-docs").exchange().getResponse().getContentAsString());

        if (Boolean.getBoolean("boardkeeper.openapi.update")) {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, current);
            return;
        }

        assertThat(file).exists();
        assertThat(normalise(Files.readString(file)))
                .as("%s is out of date. If the API change is intended, run "
                        + "'./mvnw verify -Dboardkeeper.openapi.update=true' and commit the file.", file)
                .isEqualTo(current);
    }

    /** The same JSON with sorted keys and fixed formatting, so that only real changes show up. */
    private static String normalise(String json) {
        return JSON.writeValueAsString(JSON.readValue(json, Object.class)).replace("\r\n", "\n") + "\n";
    }
}
