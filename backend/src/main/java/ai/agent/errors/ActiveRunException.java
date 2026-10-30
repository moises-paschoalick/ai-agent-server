package ai.agent.errors;

import org.springframework.http.HttpStatus;

/** 400: the thread has an active run. */
public class ActiveRunException extends ApiException {

    public ActiveRunException(String message) {
        super(HttpStatus.BAD_REQUEST, "invalid_request_error", "invalid_request", message);
    }
}
