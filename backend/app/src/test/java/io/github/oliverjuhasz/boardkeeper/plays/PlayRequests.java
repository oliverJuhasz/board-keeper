package io.github.oliverjuhasz.boardkeeper.plays;

import java.io.UnsupportedEncodingException;
import java.util.UUID;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The request bodies that the tests of the Plays API send, and a way to read its answers. */
final class PlayRequests {

    static final String COMPETITIVE = "{}";
    static final String COOPERATIVE = "{\"cooperative\": \"true\"}";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private PlayRequests() {}

    static String play(UUID game, String recordingMode, String setup, String playedOn, String... playerEntries) {
        return """
                {"gameId": "%s", "playedOn": "%s", "recordingMode": "%s", "setup": %s, "players": [%s]}
                """.formatted(game, playedOn, recordingMode, setup, String.join(", ", playerEntries));
    }

    /** A competitive Play in points on the given day. */
    static String pointsPlay(UUID game, String playedOn, String... playerEntries) {
        return play(game, "POINTS", COMPETITIVE, playedOn, playerEntries);
    }

    static String scored(UUID player, int total) {
        return "{\"playerId\": \"%s\", \"scores\": {\"total\": %d}, \"winner\": false}".formatted(player, total);
    }

    /** A Player with a score whom the recorder also marked as the winner, to decide a tie. */
    static String tieWinner(UUID player, int total) {
        return "{\"playerId\": \"%s\", \"scores\": {\"total\": %d}, \"winner\": true}".formatted(player, total);
    }

    static String winner(UUID player) {
        return "{\"playerId\": \"%s\", \"winner\": true}".formatted(player);
    }

    static String notScored(UUID player) {
        return "{\"playerId\": \"%s\", \"winner\": false}".formatted(player);
    }

    static JsonNode json(MvcTestResult result) {
        try {
            return JSON.readTree(result.getResponse().getContentAsString());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The id of the Play in an answer. */
    static String idOf(MvcTestResult result) {
        return json(result).get("id").asString();
    }
}
