package io.github.oliverjuhasz.boardkeeper.plays;

import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.PLAY;
import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.calicoPad;
import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.idOf;
import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.pointsPlay;
import static io.github.oliverjuhasz.boardkeeper.plays.PlayRequests.scored;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.oliverjuhasz.boardkeeper.TestBrowser;
import io.github.oliverjuhasz.boardkeeper.TestDatabase;
import io.github.oliverjuhasz.boardkeeper.TestcontainersConfiguration;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.TestAccounts;
import io.github.oliverjuhasz.boardkeeper.gameapi.RecordingMode;
import io.github.oliverjuhasz.boardkeeper.games.Game.RecordingDefaults;
import io.github.oliverjuhasz.boardkeeper.games.Games;
import io.github.oliverjuhasz.boardkeeper.players.Players;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Plays of a Game with a Game Module of its own, with Calico as the example. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ModuleGamePlaysApiTests {

    private static final String CALICO_MODULE_ID = "d392b4e5-a531-44c4-85c7-369783d9be5c";
    private static final String GENERIC_MODULE_ID = "38b119d2-8f9f-4e20-8068-cba305c06cdd";

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
    private DSLContext dsl;

    private TestBrowser member;
    private UUID anna;
    private UUID bence;
    private UUID dora;

    @BeforeEach
    void startWithThreePlayers() {
        database.empty();
        anna = accounts.playerOf(accounts.admin("anna"));
        bence = accounts.playerOf(accounts.member("bence"));
        dora = players.create("Dora").id();
        member = accounts.signedIn(mvc, "bence");
    }

    @Test
    void aPlayIsRecordedOnTheModulesScorePad() {
        UUID calico = calicoFromItsModule();

        MvcTestResult recorded = member.post(
                "/api/plays",
                pointsPlay(calico, "2026-03-14", calicoPad(anna, 28, 23, 21), calicoPad(dora, 21, 19, 18)));

        assertThat(recorded)
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"game": {"name": "Calico", "moduleKey": "calico"},
                         "gameModuleId": "%s",
                         "results": [
                           {"scores": {"designGoals": 28, "cats": 23, "buttons": 21}, "total": 72, "placing": 1, "won": true},
                           {"scores": {"designGoals": 21, "cats": 19, "buttons": 18}, "total": 58, "placing": 2, "won": false}]}
                        """.formatted(CALICO_MODULE_ID));
    }

    @Test
    void theFamilyVariantIsPartOfThePlaysSetup() {
        UUID calico = calicoFromItsModule();
        String withoutDesignGoals = """
                {"gameId": "%s", "playedOn": "2026-03-14", "recordingMode": "POINTS", "setup": {"family": "true"},
                 "players": [{"playerId": "%s", "scores": {"cats": 23, "buttons": 21}, "winner": false},
                             {"playerId": "%s", "scores": {"cats": 25, "buttons": 24}, "winner": false}]}
                """.formatted(calico, anna, dora);

        assertThat(member.post("/api/plays", withoutDesignGoals))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"setup": {"family": "true"}, "results": [{"total": 44, "placing": 2}, {"total": 49, "placing": 1}]}
                        """);
    }

    @Test
    void whatEveryGameRefusesIsReportedWithAReasonTheFrontendHasTheTextFor() {
        UUID calico = calicoFromItsModule();

        // Buttons are worth three points each.
        assertThat(member.post(
                        "/api/plays",
                        pointsPlay(calico, "2026-03-14", calicoPad(anna, 28, 23, 20), calicoPad(dora, 21, 19, 18))))
                .hasStatus(422)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"plays.invalidEntry\", \"reason\": \"plays.invalid.scoreNotAllowed\"}");
        String fivePlayers = pointsPlay(
                calico,
                "2026-03-14",
                calicoPad(anna, 1, 1, 3),
                calicoPad(bence, 1, 1, 3),
                calicoPad(dora, 1, 1, 3),
                calicoPad(players.create("Eszter").id(), 1, 1, 3),
                calicoPad(players.create("Feri").id(), 1, 1, 3));
        assertThat(member.post("/api/plays", fivePlayers))
                .hasStatus(422)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"plays.invalidEntry\", \"reason\": \"plays.invalid.tooManyPlayers\"}");
        // The Generic Module's row means nothing on Calico's pad.
        assertThat(member.post("/api/plays", pointsPlay(calico, "2026-03-14", scored(anna, 72), scored(dora, 58))))
                .hasStatus(422)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"plays.invalidEntry\", \"reason\": \"plays.invalid.notRecorded\"}");
        assertThat(dsl.fetchCount(PLAY)).isZero();
    }

    @Test
    void aPreviewShowsHowAPlayWouldEndWithoutRecordingIt() {
        String entry = """
                {"gameModuleId": "%s", "recordingMode": "POINTS", "setup": {},
                 "players": [%s, %s, %s]}
                """.formatted(CALICO_MODULE_ID, calicoPad(anna, 28, 23, 21), calicoPad(dora, 21, 19, 18), calicoPad(bence, 30, 30, 30));

        assertThat(member.post("/api/plays/preview", entry))
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo("""
                        {"cooperative": false,
                         "results": [{"playerId": "%s", "total": 72, "placing": 2, "won": false},
                                     {"playerId": "%s", "total": 58, "placing": 3, "won": false},
                                     {"playerId": "%s", "total": 90, "placing": 1, "won": true}],
                         "tieForRecorder": []}
                        """.formatted(anna, dora, bence));
        assertThat(dsl.fetchCount(PLAY)).isZero();
    }

    @Test
    void aPreviewNamesThePlayersOfATieTheRecorderHasToDecide() {
        // 72 points each. Calico decides that by cat tokens, which the pad does not hold.
        String tied = """
                {"gameModuleId": "%s", "recordingMode": "POINTS", "setup": {},
                 "players": [%s, %s, %s]}
                """.formatted(CALICO_MODULE_ID, calicoPad(anna, 28, 23, 21), calicoPad(dora, 30, 21, 21), calicoPad(bence, 1, 1, 3));

        assertThat(member.post("/api/plays/preview", tied))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"results": [{"placing": 1, "won": true}, {"placing": 1, "won": true}, {"placing": 3, "won": false}],
                         "tieForRecorder": ["%s", "%s"]}
                        """.formatted(anna, dora));

        // Once the recorder has said who won, the Placings follow, and the question stays open.
        String decided = tied.replace(calicoPad(dora, 30, 21, 21), calicoPad(dora, 30, 21, 21).replace("false", "true"));
        assertThat(member.post("/api/plays/preview", decided))
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"results": [{"placing": 2, "won": false}, {"placing": 1, "won": true}, {"placing": 3, "won": false}],
                         "tieForRecorder": ["%s", "%s"]}
                        """.formatted(anna, dora));
    }

    @Test
    void aPreviewOfTheGenericModuleWorksTheSameWay() {
        String cooperative = """
                {"gameModuleId": "%s", "recordingMode": "POINTS", "setup": {"cooperative": "true"},
                 "players": [{"playerId": "%s", "winner": false}], "group": {"won": true, "scores": {"total": 12}}}
                """.formatted(GENERIC_MODULE_ID, anna);
        assertThat(member.post("/api/plays/preview", cooperative))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"cooperative": true, "group": {"won": true, "total": 12}, "results": [{"won": true}],
                         "tieForRecorder": []}
                        """);

        String tied = """
                {"gameModuleId": "%s", "recordingMode": "POINTS", "setup": {}, "players": [%s, %s]}
                """.formatted(GENERIC_MODULE_ID, scored(anna, 50), scored(dora, 50));
        assertThat(member.post("/api/plays/preview", tied))
                .bodyJson()
                .extractingPath("$.tieForRecorder")
                .asArray()
                .containsExactly(anna.toString(), dora.toString());
    }

    @Test
    void aPreviewRefusesWhatRecordingWouldRefuse() {
        String withoutAWinner = """
                {"gameModuleId": "%s", "recordingMode": "WINNER_ONLY", "setup": {},
                 "players": [{"playerId": "%s", "winner": false}, {"playerId": "%s", "winner": false}]}
                """.formatted(CALICO_MODULE_ID, anna, dora);
        assertThat(member.post("/api/plays/preview", withoutAWinner))
                .hasStatus(422)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"plays.invalidEntry\", \"reason\": \"plays.invalid.winnerMissing\"}");

        String ofAnUnknownModule = withoutAWinner.replace(CALICO_MODULE_ID, UUID.randomUUID().toString());
        assertThat(member.post("/api/plays/preview", ofAnUnknownModule))
                .hasStatus(409)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"plays.gameModuleMissing\"}");
        assertThat(TestBrowser.open(mvc).post("/api/plays/preview", withoutAWinner)).hasStatus(401);
    }

    @Test
    void aPlayRecordedBeforeTheModuleArrivedKeepsTheGenericModule() {
        // Someone added Calico by hand and recorded a Play of it, with totals only.
        UUID calico = games.create("Calico", new RecordingDefaults(false, RecordingMode.POINTS, false)).id();
        String old = idOf(member.post("/api/plays", pointsPlay(calico, "2026-03-01", scored(anna, 72), scored(dora, 58))));

        // Then a build with the Calico module starts.
        games.addGamesOfModules();

        // The old Play is as it was, and is still edited on the Generic Module's pad.
        assertThat(member.get("/api/plays/" + old))
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"game": {"id": "%s", "moduleKey": "calico"}, "gameModuleId": "%s",
                         "results": [{"scores": {"total": 72}}, {"scores": {"total": 58}}]}
                        """.formatted(calico, GENERIC_MODULE_ID));
        assertThat(member.put("/api/plays/" + old, pointsPlay(calico, "2026-03-01", scored(anna, 70), scored(dora, 58))))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"gameModuleId\": \"%s\", \"results\": [{\"total\": 70}, {\"total\": 58}]}"
                        .formatted(GENERIC_MODULE_ID));

        // A new Play of the same Game goes on Calico's pad.
        assertThat(member.post(
                        "/api/plays",
                        pointsPlay(calico, "2026-03-14", calicoPad(anna, 28, 23, 21), calicoPad(dora, 21, 19, 18))))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"gameModuleId\": \"%s\", \"results\": [{\"total\": 72}, {\"total\": 58}]}"
                        .formatted(CALICO_MODULE_ID));
    }

    @Test
    void aSoloPlayForScoreOnlyIsNeitherWonNorLost() {
        UUID calico = gameOfModule("calico");

        MvcTestResult recorded = member.post("/api/plays", pointsPlay(calico, "2026-03-14", calicoPad(anna, 28, 23, 21)));

        assertThat(recorded)
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"results\": [{\"total\": 72, \"markedWinner\": false}]}");
        assertThat(recorded).bodyJson().doesNotHavePath("$.results[0].placing");
        assertThat(recorded).bodyJson().doesNotHavePath("$.results[0].won");
        // Reading it back from the database gives the same.
        assertThat(member.get("/api/plays/" + idOf(recorded))).bodyJson().doesNotHavePath("$.results[0].won");
    }

    @Test
    void aSoloPlayAgainstTheAutomaIsWonByBeatingItsScore() {
        UUID wyrmspan = gameOfModule("wyrmspan");
        String againstTheAutoma = """
                {"gameId": "%s", "playedOn": "2026-03-14", "recordingMode": "POINTS",
                 "setup": {"automaLevel": "2", "ravelMode": "false"},
                 "players": [{"playerId": "%s", "winner": false, "scores": {
                   "dragonGuild": 12, "printedDragons": 40, "endGameAbilities": 6, "eggs": 9, "cachedResources": 7,
                   "tuckedCards": 5, "publicObjectives": 11, "coinsAndItems": 3, "automa": %d}}]}
                """;

        assertThat(member.post("/api/plays", againstTheAutoma.formatted(wyrmspan, anna, 85)))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"setup": {"automaLevel": "2", "ravelMode": "false"},
                         "results": [{"scores": {"automa": 85}, "total": 93, "won": true}]}
                        """);
        // A tie is won, too, and a higher score than the Player's is lost.
        assertThat(member.post("/api/plays", againstTheAutoma.formatted(wyrmspan, anna, 93)))
                .bodyJson()
                .extractingPath("$.results[0].won")
                .isEqualTo(true);
        assertThat(member.post("/api/plays", againstTheAutoma.formatted(wyrmspan, anna, 94)))
                .bodyJson()
                .extractingPath("$.results[0].won")
                .isEqualTo(false);
    }

    @Test
    void theAutomaIsNotPartOfAPlayWithMorePlayers() {
        UUID wyrmspan = gameOfModule("wyrmspan");
        String withLevel = """
                {"gameId": "%s", "playedOn": "2026-03-14", "recordingMode": "WINNER_ONLY",
                 "setup": {"automaLevel": "2"},
                 "players": [{"playerId": "%s", "winner": true}, {"playerId": "%s", "winner": false}]}
                """.formatted(wyrmspan, anna, dora);

        assertThat(member.post("/api/plays", withLevel))
                .hasStatus(422)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"plays.invalidEntry\", \"reason\": \"plays.invalid.notRecorded\"}");
        assertThat(member.post("/api/plays", withLevel.replace("{\"automaLevel\": \"2\"}", "{}"))).hasStatusOk();
    }

    @Test
    void aPlayOfAGameWonInSeveralWaysSaysHowItWasWon() {
        UUID duel = gameOfModule("seven-wonders-duel");
        String pad = """
                {"civilian": %d, "scientific": 8, "commercial": 5, "grandTemples": 12, "divinities": 9, "wonders": 15,
                 "progressTokens": 4, "coins": 3, "military": 2}""";

        MvcTestResult recorded = member.post("/api/plays", """
                {"gameId": "%s", "playedOn": "2026-03-14", "recordingMode": "POINTS",
                 "setup": {"pantheon": "true"}, "victoryType": "civilian",
                 "players": [{"playerId": "%s", "winner": false, "scores": %s},
                             {"playerId": "%s", "winner": false, "scores": %s}]}
                """.formatted(duel, anna, pad.formatted(20), bence, pad.formatted(23)));

        // Level on points, and the blue cards decide.
        assertThat(recorded)
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"setup": {"pantheon": "true"}, "victoryType": "civilian",
                         "results": [{"total": 78, "placing": 2, "won": false}, {"total": 81, "placing": 1, "won": true}]}
                        """);
        assertThat(member.get("/api/plays/" + idOf(recorded)))
                .bodyJson()
                .extractingPath("$.victoryType")
                .isEqualTo("civilian");
    }

    @Test
    void aPlayWonOutrightHasOneWinnerAndNoScores() {
        UUID duel = gameOfModule("seven-wonders-duel");
        String outright = """
                {"gameId": "%s", "playedOn": "2026-03-14", "recordingMode": "WINNER_ONLY",
                 "setup": %s, "victoryType": "%s",
                 "players": [{"playerId": "%s", "winner": true}, {"playerId": "%s", "winner": %s}]}
                """;

        assertThat(member.post("/api/plays", outright.formatted(duel, "{}", "militarySupremacy", anna, dora, false)))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"victoryType": "militarySupremacy",
                         "results": [{"won": true, "markedWinner": true}, {"won": false}]}
                        """);
        assertThat(member.post("/api/plays", outright.formatted(duel, "{}", "militarySupremacy", anna, dora, true)))
                .hasStatus(422)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"plays.invalidEntry\", \"reason\": \"plays.invalid.oneWinnerOnly\"}");
        // Political supremacy comes with Agora.
        assertThat(member.post("/api/plays", outright.formatted(duel, "{}", "politicalSupremacy", anna, dora, false)))
                .hasStatus(422)
                .bodyJson()
                .isLenientlyEqualTo("{\"reason\": \"plays.invalid.notRecorded\"}");
        assertThat(member.post(
                        "/api/plays", outright.formatted(duel, "{\"agora\": \"true\"}", "politicalSupremacy", anna, dora, false)))
                .hasStatusOk();
    }

    @Test
    void aPlayOfAGameWonInSeveralWaysCannotLeaveOutHowItWasWon() {
        UUID duel = gameOfModule("seven-wonders-duel");

        assertThat(member.post("/api/plays", """
                        {"gameId": "%s", "playedOn": "2026-03-14", "recordingMode": "WINNER_ONLY", "setup": {},
                         "players": [{"playerId": "%s", "winner": true}, {"playerId": "%s", "winner": false}]}
                        """.formatted(duel, anna, dora)))
                .hasStatus(422)
                .bodyJson()
                .isLenientlyEqualTo("{\"reason\": \"plays.invalid.victoryTypeMissing\"}");
        // The preview says the same.
        assertThat(member.post("/api/plays/preview", """
                        {"gameModuleId": "%s", "recordingMode": "WINNER_ONLY", "setup": {}, "victoryType": "scientificSupremacy",
                         "players": [{"playerId": "%s", "winner": true}, {"playerId": "%s", "winner": false}]}
                        """.formatted("9e74ba91-e100-4b33-85d8-2d021f61eb38", anna, dora)))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"results\": [{\"won\": true}, {\"won\": false}]}");
    }

    @Test
    void eachPlayerCanHaveAPlayerSetup() {
        UUID sevenWonders = gameOfModule("seven-wonders");
        String play = """
                {"gameId": "%s", "playedOn": "2026-03-14", "recordingMode": "POINTS", "setup": {"cities": "true"},
                 "players": [
                   {"playerId": "%s", "winner": false, "setup": {"wonder": "babylon", "side": "night"},
                    "scores": {"wonder": 10, "treasury": -3, "military": -6, "civilian": 15, "commercial": 5,
                               "scientific": 13, "guilds": 7, "cities": 4}},
                   {"playerId": "%s", "winner": false, "setup": {"wonder": "%s"},
                    "scores": {"wonder": 7, "treasury": 2, "military": 5, "civilian": 20, "commercial": 8,
                               "scientific": 1, "guilds": 4, "cities": 0}},
                   {"playerId": "%s", "winner": false,
                    "scores": {"wonder": 3, "treasury": 6, "military": 0, "civilian": 12, "commercial": 2,
                               "scientific": 25, "guilds": 6, "cities": 2}}]}
                """;

        MvcTestResult recorded = member.post("/api/plays", play.formatted(sevenWonders, anna, bence, "petra", dora));
        assertThat(recorded)
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"results": [
                          {"setup": {"wonder": "babylon", "side": "night"}, "total": 45, "placing": 3},
                          {"setup": {"wonder": "petra"}, "total": 47, "placing": 2},
                          {"setup": {}, "total": 56, "placing": 1, "won": true}]}
                        """);
        assertThat(member.get("/api/plays/" + idOf(recorded)))
                .bodyJson()
                .extractingPath("$.results[0].setup.wonder")
                .isEqualTo("babylon");
        // Each Wonder exists once.
        assertThat(member.post("/api/plays", play.formatted(sevenWonders, anna, bence, "babylon", dora)))
                .hasStatus(422)
                .bodyJson()
                .isLenientlyEqualTo("{\"reason\": \"plays.invalid.chosenTwice\"}");
        // Petra comes with Cities.
        String withoutCities = """
                {"gameId": "%s", "playedOn": "2026-03-14", "recordingMode": "POINTS", "setup": {},
                 "players": [
                   {"playerId": "%s", "winner": false, "setup": {"wonder": "petra"},
                    "scores": {"wonder": 10, "treasury": 3, "military": 6, "civilian": 15, "commercial": 5,
                               "scientific": 13, "guilds": 7}},
                   {"playerId": "%s", "winner": false,
                    "scores": {"wonder": 7, "treasury": 2, "military": 5, "civilian": 20, "commercial": 8,
                               "scientific": 1, "guilds": 4}},
                   {"playerId": "%s", "winner": false,
                    "scores": {"wonder": 3, "treasury": 6, "military": 0, "civilian": 12, "commercial": 2,
                               "scientific": 25, "guilds": 6}}]}
                """.formatted(sevenWonders, anna, bence, dora);
        assertThat(member.post("/api/plays", withoutCities))
                .hasStatus(422)
                .bodyJson()
                .isLenientlyEqualTo("{\"reason\": \"plays.invalid.needsExpansion\"}");
    }

    @Test
    void aCooperativeGameWithAModuleOfItsOwnWorksOutTheGroupsScore() {
        UUID spiritIsland = gameOfModule("spirit-island");
        String play = """
                {"gameId": "%s", "playedOn": "2026-03-14", "recordingMode": "%s",
                 "setup": {"adversary": "england", "englandLevel": "3"},
                 "players": [{"playerId": "%s", "winner": false, "setup": {"spirit": "thunderspeaker"}},
                             {"playerId": "%s", "winner": false, "setup": {"spirit": "oceansHungryGrasp"}}],
                 "group": %s}
                """;

        // Without a score: only whether the Spirits won.
        assertThat(member.post("/api/plays", play.formatted(spiritIsland, "WINNER_ONLY", anna, dora, "{\"won\": true}")))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"cooperative": true, "group": {"won": true}, "results": [{"won": true}, {"won": true}]}
                        """);
        // With it: 5 × 6 + 10 + 2 × 4, + 7 Dahan for two (3) − 5 Blight for two (2).
        MvcTestResult scored = member.post(
                "/api/plays",
                play.formatted(
                        spiritIsland,
                        "POINTS",
                        anna,
                        dora,
                        "{\"won\": true, \"scores\": {\"invaderCards\": 4, \"dahan\": 7, \"blight\": 5}}"));
        assertThat(scored)
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"group": {"won": true, "scores": {"invaderCards": 4, "dahan": 7, "blight": 5}, "total": 49},
                         "results": [{"setup": {"spirit": "thunderspeaker"}}, {"setup": {"spirit": "oceansHungryGrasp"}}]}
                        """);
        assertThat(member.get("/api/plays/" + idOf(scored))).bodyJson().extractingPath("$.group.total").isEqualTo(49);
        // An adversary's level goes with that adversary only.
        assertThat(member.post("/api/plays", play.formatted(spiritIsland, "WINNER_ONLY", anna, dora, "{\"won\": true}")
                        .replace("\"adversary\": \"england\"", "\"adversary\": \"sweden\"")))
                .hasStatus(422)
                .bodyJson()
                .isLenientlyEqualTo("{\"reason\": \"plays.invalid.notRecorded\"}");
    }

    @Test
    void aCooperativeGameThatScoresOnlyAVictoryHasNoScoreAfterADefeat() {
        UUID lastBastion = gameOfModule("last-bastion");
        String play = """
                {"gameId": "%s", "playedOn": "2026-03-14", "recordingMode": "POINTS", "setup": {"difficulty": "heroic"},
                 "players": [{"playerId": "%s", "winner": false, "setup": {"hero": "kazgin"}},
                             {"playerId": "%s", "winner": false, "setup": {"hero": "twindalli"}}],
                 "group": %s}
                """;

        // 20 for Heroic, + 5 Life tokens + 2 × 1 Call to Arms token − 3 × 1 dead hero − 4 × 2 tiles.
        assertThat(member.post(
                        "/api/plays",
                        play.formatted(
                                lastBastion,
                                anna,
                                dora,
                                """
                                {"won": true,
                                 "scores": {"lifeTokens": 5, "callToArmsTokens": 1, "deadHeroes": 1, "graspOfEvilTiles": 2}}
                                """)))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"recordingMode\": \"POINTS\", \"group\": {\"won\": true, \"total\": 16}}");
        MvcTestResult defeat = member.post("/api/plays", play.formatted(lastBastion, anna, dora, "{\"won\": false}"));
        assertThat(defeat)
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"recordingMode\": \"POINTS\", \"group\": {\"won\": false, \"scores\": {}}}");
        assertThat(member.get("/api/plays/" + idOf(defeat))).bodyJson().doesNotHavePath("$.group.total");
        // Both heroes dead is a defeat, not a victory.
        assertThat(member.post(
                        "/api/plays",
                        play.formatted(
                                lastBastion,
                                anna,
                                dora,
                                """
                                {"won": true,
                                 "scores": {"lifeTokens": 0, "callToArmsTokens": 0, "deadHeroes": 2, "graspOfEvilTiles": 0}}
                                """)))
                .hasStatus(422)
                .bodyJson()
                .isLenientlyEqualTo("{\"reason\": \"last-bastion.invalid.tooManyDeadHeroes\"}");
    }

    /** The Game that appears in the catalogue for the Calico module. */
    private UUID calicoFromItsModule() {
        return gameOfModule("calico");
    }

    /** The Game that appears in the catalogue for the Game Module with this key. */
    private UUID gameOfModule(String key) {
        games.addGamesOfModules();
        return games.all().stream()
                .filter(game -> games.moduleKeyOf(game).filter(key::equals).isPresent())
                .findFirst()
                .orElseThrow()
                .id();
    }
}
