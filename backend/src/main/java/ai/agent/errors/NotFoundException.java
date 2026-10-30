package ai.agent.errors;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "invalid_request_error", "not_found", message);
    }
}
