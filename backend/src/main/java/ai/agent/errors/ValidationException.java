package ai.agent.errors;

import org.springframework.http.HttpStatus;

public class ValidationException extends ApiException {

    public ValidationException(String message) {
        super(HttpStatus.BAD_REQUEST, "invalid_request_error", "invalid_request", message);
    }

    /** {@code param} names the offending field (shown in /v1 errors). */
    public ValidationException(String message, String param) {
        super(HttpStatus.BAD_REQUEST, "invalid_request_error", "invalid_request", param, message);
    }
}
