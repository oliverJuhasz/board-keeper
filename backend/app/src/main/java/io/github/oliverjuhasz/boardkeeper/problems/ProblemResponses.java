package io.github.oliverjuhasz.boardkeeper.problems;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;

/**
 * Writes an error answer with a code directly to a response, in the same format as a thrown
 * {@link Problem}. For the places that refuse a request before it reaches a controller, where
 * throwing a Problem is not possible: Spring Security's handling of "not signed in" and
 * "not allowed".
 */
public final class ProblemResponses {

    private ProblemResponses() {}

    public static void write(HttpServletResponse response, HttpStatus status, String code) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        // The code comes from our own source, never from the request, so it needs no escaping.
        response.getWriter().write("{\"status\":%d,\"code\":\"%s\"}".formatted(status.value(), code));
    }
}
