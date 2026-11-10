package io.github.oliverjuhasz.boardkeeper.language;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Optional;

/** A language the user interface is available in. */
public enum Language {
    ENGLISH("en"),
    DUTCH("nl"),
    HUNGARIAN("hu");

    /** The language used when a text is missing in another one. */
    public static final Language FALLBACK = ENGLISH;

    private final String code;

    Language(String code) {
        this.code = code;
    }

    /**
     * The two-letter code, such as {@code hu}. {@code @JsonValue} makes it the form the language
     * takes in the API, both in JSON and in the API's description.
     */
    @JsonValue
    public String code() {
        return code;
    }

    public static Optional<Language> fromCode(String code) {
        return Arrays.stream(values())
                .filter(language -> language.code.equals(code))
                .findFirst();
    }
}
