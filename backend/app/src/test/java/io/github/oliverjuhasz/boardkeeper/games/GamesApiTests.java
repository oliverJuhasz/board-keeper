package io.github.oliverjuhasz.boardkeeper.games;

import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.GAME;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.oliverjuhasz.boardkeeper.TestcontainersConfiguration;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.annotation.Transactional;

/**
 * Starts the whole application against a real PostgreSQL and calls the API as a client would.
 * {@code @Transactional} rolls each test's database changes back when the test ends.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class GamesApiTests {

    private static final UUID GENERIC_MODULE_ID = UUID.fromString("38b119d2-8f9f-4e20-8068-cba305c06cdd");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private DSLContext dsl;

    @Test
    void listsTheInstalledGameModules() {
        assertThat(mvc.get().uri("/api/game-modules"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        [{"id": "38b119d2-8f9f-4e20-8068-cba305c06cdd", "key": "generic"}]
                        """);
    }

    @Test
    void listsTheGamesInTheCatalogueByName() {
        dsl.insertInto(GAME, GAME.NAME, GAME.GAME_MODULE_ID)
                .values("Wyrmspan", GENERIC_MODULE_ID)
                .values("Calico", GENERIC_MODULE_ID)
                .execute();

        assertThat(mvc.get().uri("/api/games"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[*].name")
                .asArray()
                .containsExactly("Calico", "Wyrmspan");
    }

    @Test
    void anUnknownApiAddressIsNotFound() {
        assertThat(mvc.get().uri("/api/no-such-thing")).hasStatus(404);
    }
}
