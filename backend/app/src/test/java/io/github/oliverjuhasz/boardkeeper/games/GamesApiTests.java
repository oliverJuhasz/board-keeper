package io.github.oliverjuhasz.boardkeeper.games;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.oliverjuhasz.boardkeeper.TestBrowser;
import io.github.oliverjuhasz.boardkeeper.TestDatabase;
import io.github.oliverjuhasz.boardkeeper.TestcontainersConfiguration;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.TestAccounts;
import io.github.oliverjuhasz.boardkeeper.gameapi.RecordingMode;
import io.github.oliverjuhasz.boardkeeper.games.Game.RecordingDefaults;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GamesApiTests {

    private static final String GENERIC_MODULE_ID = "38b119d2-8f9f-4e20-8068-cba305c06cdd";
    private static final String CALICO_MODULE_ID = "d392b4e5-a531-44c4-85c7-369783d9be5c";
    private static final RecordingDefaults POINTS = new RecordingDefaults(false, RecordingMode.POINTS, false);

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private TestDatabase database;

    @Autowired
    private TestAccounts accounts;

    @Autowired
    private Games games;

    private TestBrowser admin;
    private TestBrowser member;

    @BeforeEach
    void startWithAnAdminAndAMember() {
        database.empty();
        accounts.admin("anna");
        accounts.member("bence");
        admin = accounts.signedIn(mvc, "anna");
        member = accounts.signedIn(mvc, "bence");
    }

    @Test
    void listsTheInstalledGameModulesWithTheirScorePads() {
        assertThat(member.get("/api/game-modules"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        [{"id": "%s", "key": "calico",
                          "scorePad": {
                            "minPlayers": 1, "maxPlayers": 4, "solo": {"kind": "FOR_SCORE_ONLY"},
                            "variants": [{"key": "family", "kind": "ON_OFF", "defaultValue": "false"}],
                            "categories": [
                              {"key": "designGoals", "countsTowardTotal": true, "min": 0, "step": 1,
                               "onlyWhen": {"key": "family", "value": "false"}},
                              {"key": "cats", "countsTowardTotal": true, "min": 0, "step": 1},
                              {"key": "buttons", "countsTowardTotal": true, "min": 0, "step": 3}],
                            "recorderDecidesTies": true}},
                         {"id": "%s", "key": "generic",
                          "scorePad": {
                            "minPlayers": 1,
                            "variants": [{"key": "cooperative", "kind": "ON_OFF"}, {"key": "lowestScoreWins", "kind": "ON_OFF"}],
                            "categories": [{"key": "total", "countsTowardTotal": true, "step": 1}],
                            "recorderDecidesTies": true}},
                         {"key": "hadara",
                          "scorePad": {
                            "minPlayers": 1, "maxPlayers": 5,
                            "solo": {"kind": "AGAINST_OPPONENT", "opponentCategory": "npc", "tieWins": true},
                            "variants": [{"key": "npcLevel", "kind": "CHOICE",
                                          "options": ["1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "godMode"],
                                          "defaultValue": "1", "soloOnly": true}],
                            "categories": [
                              {"key": "colonies"}, {"key": "statues"}, {"key": "silverMedals"},
                              {"key": "goldMedals", "step": 7}, {"key": "cards"}, {"key": "remainingCoins"},
                              {"key": "npc", "countsTowardTotal": false, "soloOnly": true}],
                            "recorderDecidesTies": true}},
                         {"key": "harmonies",
                          "scorePad": {
                            "variants": [{"key": "boardSide", "kind": "CHOICE", "options": ["a", "b"], "defaultValue": "a"},
                                         {"key": "naturesSpirit", "kind": "ON_OFF", "defaultValue": "false"}],
                            "categories": [
                              {"key": "trees"}, {"key": "mountains"}, {"key": "fields", "step": 5},
                              {"key": "buildings", "step": 5},
                              {"key": "river", "onlyWhen": {"key": "boardSide", "value": "a"}},
                              {"key": "islands", "min": 5, "step": 5, "onlyWhen": {"key": "boardSide", "value": "b"}},
                              {"key": "animals"},
                              {"key": "naturesSpirit", "onlyWhen": {"key": "naturesSpirit", "value": "true"}}],
                            "expansions": [], "victoryTypes": []}},
                         {"key": "last-bastion",
                          "scorePad": {
                            "minPlayers": 1, "maxPlayers": 4, "cooperative": true, "usualRecordingMode": "POINTS",
                            "variants": [{"key": "difficulty", "kind": "CHOICE",
                                          "options": ["novice", "normal", "difficult", "heroic"], "defaultValue": "normal"}],
                            "categories": [
                              {"key": "lifeTokens", "afterVictoryOnly": true},
                              {"key": "callToArmsTokens", "afterVictoryOnly": true},
                              {"key": "deadHeroes", "max": 3, "afterVictoryOnly": true},
                              {"key": "graspOfEvilTiles", "max": 2, "afterVictoryOnly": true}],
                            "playerSetup": [{"key": "hero", "oncePerPlay": true}]}},
                         {"key": "lords-of-waterdeep",
                          "scorePad": {
                            "minPlayers": 2, "maxPlayers": 5,
                            "expansions": [{"key": "undermountain", "maxPlayers": 6}, {"key": "skullport", "maxPlayers": 6}],
                            "categories": [
                              {"key": "scoringTrack"}, {"key": "adventurers"}, {"key": "gold"}, {"key": "lordCard"},
                              {"key": "corruption", "max": 0, "onlyWhen": {"key": "skullport", "value": "true"}}],
                            "recorderDecidesTies": true,
                            "playerSetup": [
                              {"key": "lord", "oncePerPlay": true,
                               "optionsOnlyWith": {
                                 "daniloThann": "undermountain", "halasterBlackcloak": "undermountain",
                                 "trobriand": "undermountain", "irusylEraneth": "skullport", "sangalor": "skullport",
                                 "theXanathar": "skullport"}}]}},
                         {"key": "seven-wonders",
                          "scorePad": {
                            "minPlayers": 3, "maxPlayers": 7,
                            "expansions": [{"key": "leaders"}, {"key": "cities"}],
                            "categories": [
                              {"key": "wonder", "min": 0}, {"key": "treasury"}, {"key": "military"},
                              {"key": "civilian"}, {"key": "commercial"}, {"key": "scientific"}, {"key": "guilds"},
                              {"key": "cities", "onlyWhen": {"key": "cities", "value": "true"}},
                              {"key": "leaders", "onlyWhen": {"key": "leaders", "value": "true"}}],
                            "recorderDecidesTies": true,
                            "victoryTypes": [],
                            "playerSetup": [
                              {"key": "wonder", "oncePerPlay": true,
                               "options": ["alexandria", "babylon", "ephesus", "giza", "halicarnassus", "olympia", "rhodes",
                                           "roma", "abuSimbel", "byzantium", "petra", "mannekenPis"]},
                              {"key": "side", "oncePerPlay": false, "options": ["day", "night"]}]}},
                         {"key": "seven-wonders-duel",
                          "scorePad": {
                            "minPlayers": 2, "maxPlayers": 2,
                            "expansions": [{"key": "pantheon"}, {"key": "agora"}],
                            "variants": [],
                            "categories": [
                              {"key": "civilian"}, {"key": "scientific"}, {"key": "commercial"},
                              {"key": "guilds", "onlyWhen": {"key": "pantheon", "value": "false"}},
                              {"key": "grandTemples", "max": 21, "onlyWhen": {"key": "pantheon", "value": "true"}},
                              {"key": "divinities", "onlyWhen": {"key": "pantheon", "value": "true"}},
                              {"key": "wonders"}, {"key": "progressTokens"}, {"key": "coins"},
                              {"key": "military", "max": 10},
                              {"key": "senate", "onlyWhen": {"key": "agora", "value": "true"}}],
                            "recorderDecidesTies": false,
                            "victoryTypes": [
                              {"key": "civilian", "onPoints": true},
                              {"key": "militarySupremacy", "onPoints": false},
                              {"key": "scientificSupremacy", "onPoints": false},
                              {"key": "politicalSupremacy", "onPoints": false,
                               "onlyWhen": {"key": "agora", "value": "true"}}]}},
                         {"key": "spirit-island",
                          "scorePad": {
                            "minPlayers": 1, "maxPlayers": 4, "cooperative": true, "usualRecordingMode": "WINNER_ONLY",
                            "expansions": [],
                            "variants": [
                              {"key": "adversary", "kind": "CHOICE", "defaultValue": "none",
                               "options": ["none", "brandenburgPrussia", "england", "sweden"]},
                              {"key": "brandenburgPrussiaLevel", "kind": "RANGE", "min": 0, "max": 6, "defaultValue": "0",
                               "onlyWhen": {"key": "adversary", "value": "brandenburgPrussia"}},
                              {"key": "englandLevel", "kind": "RANGE", "onlyWhen": {"key": "adversary", "value": "england"}},
                              {"key": "swedenLevel", "kind": "RANGE", "onlyWhen": {"key": "adversary", "value": "sweden"}},
                              {"key": "scenario", "kind": "CHOICE"},
                              {"key": "thematicMap", "kind": "ON_OFF", "defaultValue": "false"}],
                            "categories": [{"key": "invaderCards"}, {"key": "dahan"}, {"key": "blight"}],
                            "victoryTypes": [],
                            "playerSetup": [{"key": "spirit", "oncePerPlay": true}]}},
                         {"key": "wyrmspan",
                          "scorePad": {
                            "minPlayers": 1, "maxPlayers": 5,
                            "solo": {"kind": "AGAINST_OPPONENT", "opponentCategory": "automa", "tieWins": true},
                            "variants": [{"key": "automaLevel", "kind": "CHOICE", "options": ["1", "2", "3"], "soloOnly": true},
                                         {"key": "ravelMode", "kind": "ON_OFF", "soloOnly": true}],
                            "categories": [
                              {"key": "dragonGuild", "soloOnly": false}, {"key": "printedDragons"},
                              {"key": "endGameAbilities"}, {"key": "eggs"}, {"key": "cachedResources"},
                              {"key": "tuckedCards"}, {"key": "publicObjectives"}, {"key": "coinsAndItems"},
                              {"key": "automa", "countsTowardTotal": false, "soloOnly": true}],
                            "recorderDecidesTies": true}}]
                        """.formatted(CALICO_MODULE_ID, GENERIC_MODULE_ID));
    }

    @Test
    void aGameWithAModuleOfItsOwnAppearsInTheCatalogueByItself() {
        games.addGamesOfModules();

        assertThat(member.get("/api/games"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        [{"name": "7 Wonders", "moduleKey": "seven-wonders", "hasOwnModule": true},
                         {"name": "7 Wonders Duel", "moduleKey": "seven-wonders-duel", "hasOwnModule": true},
                         {"name": "Calico", "gameModuleId": "%s", "moduleKey": "calico", "hasOwnModule": true},
                         {"name": "Hadara", "moduleKey": "hadara", "hasOwnModule": true},
                         {"name": "Harmonies", "moduleKey": "harmonies", "hasOwnModule": true},
                         {"name": "Last Bastion", "moduleKey": "last-bastion", "hasOwnModule": true},
                         {"name": "Lords of Waterdeep", "moduleKey": "lords-of-waterdeep", "hasOwnModule": true},
                         {"name": "Spirit Island", "moduleKey": "spirit-island", "hasOwnModule": true},
                         {"name": "Wyrmspan", "moduleKey": "wyrmspan", "hasOwnModule": true}]
                        """.formatted(CALICO_MODULE_ID));

        // Bringing the catalogue in step again changes nothing.
        games.addGamesOfModules();
        assertThat(games.all()).hasSize(9);
    }

    @Test
    void aGameWithAModuleOfItsOwnCanBeNeitherEditedNorRemoved() {
        games.addGamesOfModules();
        UUID calico = games.all().stream()
                .filter(game -> game.name().equals("Calico"))
                .findFirst()
                .orElseThrow()
                .id();

        assertThat(admin.put("/api/games/" + calico, game("Calico 2", false, "POINTS", false)))
                .hasStatus(409)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"games.hasOwnModule\"}");
        assertThat(admin.delete("/api/games/" + calico))
                .hasStatus(409)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"games.hasOwnModule\"}");
        assertThat(games.find(calico).orElseThrow().name()).isEqualTo("Calico");
        // Its name is taken, too.
        assertThat(member.post("/api/games", game("calico", false, "POINTS", false))).hasStatus(409);
    }

    @Test
    void aGameAddedByHandIsHandedToTheModuleThatNamesIt() {
        UUID byHand = games.create("calico", POINTS).id();
        games.create("Azul", POINTS);

        games.addGamesOfModules();

        // The same Game, under the name it was given, now scored by the Calico module.
        assertThat(member.get("/api/games"))
                .bodyJson()
                .isLenientlyEqualTo("""
                        [{"name": "7 Wonders", "moduleKey": "seven-wonders", "hasOwnModule": true},
                         {"name": "7 Wonders Duel", "moduleKey": "seven-wonders-duel", "hasOwnModule": true},
                         {"name": "Azul", "moduleKey": "generic", "hasOwnModule": false},
                         {"id": "%s", "name": "calico", "moduleKey": "calico", "hasOwnModule": true},
                         {"name": "Hadara", "moduleKey": "hadara", "hasOwnModule": true},
                         {"name": "Harmonies", "moduleKey": "harmonies", "hasOwnModule": true},
                         {"name": "Last Bastion", "moduleKey": "last-bastion", "hasOwnModule": true},
                         {"name": "Lords of Waterdeep", "moduleKey": "lords-of-waterdeep", "hasOwnModule": true},
                         {"name": "Spirit Island", "moduleKey": "spirit-island", "hasOwnModule": true},
                         {"name": "Wyrmspan", "moduleKey": "wyrmspan", "hasOwnModule": true}]
                        """.formatted(byHand));
    }

    @Test
    void givesTheGameModuleTextsInTheLanguageAskedFor() {
        assertThat(member.get("/api/game-modules/texts/hu"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$['generic.name']")
                .isEqualTo("Általános");
        assertThat(member.get("/api/game-modules/texts/xx")).hasStatus(400);
    }

    @Test
    void listsTheGamesByNameWhateverTheirCapitals() {
        games.create("wyrmspan", POINTS);
        games.create("Calico", POINTS);

        assertThat(member.get("/api/games"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[*].name")
                .asArray()
                .containsExactly("Calico", "wyrmspan");
    }

    @Test
    void aGameAddedByHandIsScoredByTheGenericModule() {
        assertThat(member.post("/api/games", game("  Cascadia  ", false, "POINTS", false)))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"name": "Cascadia", "gameModuleId": "%s", "moduleKey": "generic", "hasOwnModule": false,
                         "cooperative": false, "recordingMode": "POINTS", "lowestScoreWins": false}
                        """.formatted(GENERIC_MODULE_ID));
    }

    @Test
    void aGameRemembersHowItsPlaysAreRecorded() {
        member.post("/api/games", game("Golf", false, "POINTS", true));
        member.post("/api/games", game("Pandemic", true, "WINNER_ONLY", false));

        assertThat(member.get("/api/games"))
                .bodyJson()
                .isLenientlyEqualTo("""
                        [{"name": "Golf", "cooperative": false, "recordingMode": "POINTS", "lowestScoreWins": true},
                         {"name": "Pandemic", "cooperative": true, "recordingMode": "WINNER_ONLY", "lowestScoreWins": false}]
                        """);
    }

    @Test
    void lowestScoreWinsOnlyAppliesToACompetitivePlayRecordedInPoints() {
        // Asked for where it has no meaning, it is simply not kept.
        assertThat(member.post("/api/games", game("Hanabi", true, "POINTS", true)))
                .bodyJson()
                .extractingPath("$.lowestScoreWins")
                .isEqualTo(false);
        assertThat(member.post("/api/games", game("Chess", false, "WINNER_ONLY", true)))
                .bodyJson()
                .extractingPath("$.lowestScoreWins")
                .isEqualTo(false);
    }

    @Test
    void twoGamesCannotShareANameEvenInDifferentCapitals() {
        games.create("Calico", POINTS);

        assertThat(member.post("/api/games", game("CALICO", false, "POINTS", false)))
                .hasStatus(409)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"games.nameTaken\"}");
    }

    @Test
    void aGameNeedsANameAndARecordingMode() {
        assertThat(member.post("/api/games", game("  ", false, "POINTS", false)))
                .hasStatus(400)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"request.invalid\", \"errors\": [{\"field\": \"name\"}]}");
        assertThat(member.post("/api/games", "{\"name\": \"Calico\", \"cooperative\": false, \"lowestScoreWins\": false}"))
                .hasStatus(400);
    }

    @Test
    void aMemberCanEditAGame() {
        UUID calico = games.create("calico", POINTS).id();

        assertThat(member.put("/api/games/" + calico, game("Calico", false, "WINNER_ONLY", false)))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"name\": \"Calico\", \"recordingMode\": \"WINNER_ONLY\"}");
        assertThat(games.find(calico).orElseThrow().defaults().recordingMode()).isEqualTo(RecordingMode.WINNER_ONLY);
    }

    @Test
    void aGameCannotBeRenamedToAnotherGamesName() {
        UUID calico = games.create("Calico", POINTS).id();
        games.create("Wyrmspan", POINTS);

        assertThat(member.put("/api/games/" + calico, game("wyrmspan", false, "POINTS", false)))
                .hasStatus(409)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"games.nameTaken\"}");
        assertThat(member.put("/api/games/" + UUID.randomUUID(), game("Other", false, "POINTS", false)))
                .hasStatus(404)
                .bodyJson()
                .isLenientlyEqualTo("{\"code\": \"games.notFound\"}");
    }

    @Test
    void removingAGameNeedsItsOwnPermission() {
        UUID calico = games.create("Calico", POINTS).id();

        // A Member holds "Create games" and "Edit games", but not "Remove games".
        assertThat(member.delete("/api/games/" + calico)).hasStatus(403);
        assertThat(games.find(calico)).isPresent();

        assertThat(admin.delete("/api/games/" + calico)).hasStatus(204);
        assertThat(games.find(calico)).isEmpty();
    }

    @Test
    void isClosedToAnyoneNotSignedIn() {
        assertThat(TestBrowser.open(mvc).get("/api/games")).hasStatus(401);
        assertThat(TestBrowser.open(mvc).get("/api/no-such-thing")).hasStatus(401);
    }

    private static String game(String name, boolean cooperative, String recordingMode, boolean lowestScoreWins) {
        return """
                {"name": "%s", "cooperative": %s, "recordingMode": "%s", "lowestScoreWins": %s}
                """.formatted(name, cooperative, recordingMode, lowestScoreWins);
    }
}
