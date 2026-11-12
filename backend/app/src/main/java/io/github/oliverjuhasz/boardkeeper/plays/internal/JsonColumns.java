package io.github.oliverjuhasz.boardkeeper.plays.internal;

import java.util.Map;
import org.jooq.JSONB;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Converts between Java maps and the {@code jsonb} columns of the Play tables. jOOQ hands a
 * {@code jsonb} value over as a {@link JSONB}, which is just the JSON text.
 *
 * <p>It has a JSON mapper of its own, not the application's. That one is set up for the API,
 * and a change made there for the API must not change what is stored.
 */
final class JsonColumns {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, String>> TEXTS = new TypeReference<>() {};
    private static final TypeReference<Map<String, Integer>> NUMBERS = new TypeReference<>() {};

    private JsonColumns() {}

    static JSONB toJson(Map<String, ?> map) {
        return JSONB.valueOf(JSON.writeValueAsString(map));
    }

    /** A Setup: the value chosen for each Variant. */
    static Map<String, String> toSetup(JSONB json) {
        return JSON.readValue(json.data(), TEXTS);
    }

    /** The value entered for each Score Category. */
    static Map<String, Integer> toScores(JSONB json) {
        return JSON.readValue(json.data(), NUMBERS);
    }
}
