package io.github.oliverjuhasz.boardkeeper.problems;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a request cannot be carried out for a reason the user should be told about.
 * The API answers with the given status and the code, such as {@code players.nameTaken}.
 * The backend never writes the message itself: the frontend has a text for every code, in
 * every language.
 */
public class Problem extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public Problem(HttpStatus status, String code) {
        super(code);
        this.status = status;
        this.code = code;
    }

    /** The request was fine, but clashes with what already exists (409). */
    public static Problem conflict(String code) {
        return new Problem(HttpStatus.CONFLICT, code);
    }

    /** The thing asked for does not exist (404). */
    public static Problem notFound(String code) {
        return new Problem(HttpStatus.NOT_FOUND, code);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
