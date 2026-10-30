package ai.agent.errors;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, "invalid_request_error", "conflict", message);
    }
}
