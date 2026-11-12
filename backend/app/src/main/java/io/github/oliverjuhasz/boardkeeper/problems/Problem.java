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
    private final String reason;

    public Problem(HttpStatus status, String code) {
        this(status, code, null);
    }

    private Problem(HttpStatus status, String code, String reason) {
        super(reason == null ? code : code + ": " + reason);
        this.status = status;
        this.code = code;
        this.reason = reason;
    }

    /** The request was fine, but clashes with what already exists (409). */
    public static Problem conflict(String code) {
        return new Problem(HttpStatus.CONFLICT, code);
    }

    /** The thing asked for does not exist (404). */
    public static Problem notFound(String code) {
        return new Problem(HttpStatus.NOT_FOUND, code);
    }

    /** The signed-in Account may not do this to this particular thing (403). */
    public static Problem forbidden(String code) {
        return new Problem(HttpStatus.FORBIDDEN, code);
    }

    /** The request is well-formed, but what it asks for breaks a rule (422). */
    public static Problem unprocessable(String code) {
        return new Problem(HttpStatus.UNPROCESSABLE_CONTENT, code);
    }

    /**
     * Like {@link #unprocessable(String)}, with a more exact reason that someone else has the
     * text for. A Game Module's refusal of a Play is reported this way: the reason is a name in
     * that module's translation files, such as {@code generic.invalid.winnerMissing}.
     */
    public static Problem unprocessable(String code, String reason) {
        return new Problem(HttpStatus.UNPROCESSABLE_CONTENT, code, reason);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    /** The more exact reason, or null when the code says it all. */
    public String reason() {
        return reason;
    }
}
