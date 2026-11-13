package io.github.oliverjuhasz.boardkeeper.stats.internal;

import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.STATS_PLAY;
import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.pointsPlay;
import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.scored;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.oliverjuhasz.boardkeeper.TestBrowser;
import io.github.oliverjuhasz.boardkeeper.TestDatabase;
import io.github.oliverjuhasz.boardkeeper.TestcontainersConfiguration;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.TestAccounts;
import io.github.oliverjuhasz.boardkeeper.gameapi.RecordingMode;
import io.github.oliverjuhasz.boardkeeper.games.Game.RecordingDefaults;
import io.github.oliverjuhasz.boardkeeper.games.Games;
import io.github.oliverjuhasz.boardkeeper.players.Players;
import java.time.Duration;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class StatsStartupTests {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private TestDatabase database;

    @Autowired
    private TestAccounts accounts;

    @Autowired
    private Games games;

    @Autowired
    private Players players;

    @Autowired
    private StatsStartup startup;

    @Autowired
    private DSLContext dsl;

    @Test
    void anEmptiedCopyOfThePlaysIsFilledAgainAtTheNextStart() {
        database.empty();
        accounts.member("recorder");
        TestBrowser member = accounts.signedIn(mvc, "recorder");
        UUID anna = players.create("Anna").id();
        UUID dora = players.create("Dora").id();
        UUID azul = games.create("Azul", new RecordingDefaults(false, RecordingMode.POINTS, false)).id();
        member.post("/api/plays", pointsPlay(azul, "2026-03-01", scored(anna, 50), scored(dora, 40)));
        member.post("/api/plays", pointsPlay(azul, "2026-03-08", scored(anna, 30), scored(dora, 45)));
        await().atMost(Duration.ofSeconds(10)).until(() -> dsl.fetchCount(STATS_PLAY) == 2);

        dsl.deleteFrom(STATS_PLAY).execute();
        startup.run(null);

        assertThat(member.get("/api/stats/games/" + azul))
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"plays": 2, "players": [{"name": "Anna", "plays": 2, "wins": 1, "averageScore": 40.0},
                                                 {"name": "Dora", "plays": 2, "wins": 1, "averageScore": 42.5}]}
                        """);
    }
}
