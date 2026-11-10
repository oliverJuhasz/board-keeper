package io.github.oliverjuhasz.boardkeeper.problems.internal;

import io.github.oliverjuhasz.boardkeeper.problems.Problem;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns exceptions into the API's error answers. Every answer is a "problem details" JSON
 * object (RFC 9457) with one addition: a {@code code} for the frontend to translate.
 *
 * <p>Extending {@link ResponseEntityExceptionHandler} keeps Spring's own handling of the
 * standard errors, such as a malformed request body.
 */
@RestControllerAdvice
class ProblemHandler extends ResponseEntityExceptionHandler {

    static final String INVALID_REQUEST = "request.invalid";

    @ExceptionHandler(Problem.class)
    ProblemDetail handle(Problem problem) {
        ProblemDetail detail = ProblemDetail.forStatus(problem.status());
        detail.setProperty("code", problem.code());
        return detail;
    }

    /** A request body that broke a rule such as {@code @Size(min = 16)}: say which fields, and how. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldProblem> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldProblem(error.getField(), codeOf(error.getCode())))
                .toList();
        ProblemDetail detail = exception.getBody();
        detail.setProperty("code", INVALID_REQUEST);
        detail.setProperty("errors", errors);
        return handleExceptionInternal(exception, detail, headers, status, request);
    }

    /** The name of the rule that was broken, such as "size" for {@code @Size}. */
    private static String codeOf(String constraint) {
        return constraint == null ? "invalid" : constraint.toLowerCase(Locale.ROOT);
    }

    record FieldProblem(String field, String code) {}
}
