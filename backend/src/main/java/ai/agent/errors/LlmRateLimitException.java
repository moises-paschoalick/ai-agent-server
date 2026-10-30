package ai.agent.errors;

import org.springframework.http.HttpStatus;

public class LlmRateLimitException extends ApiException {

    public LlmRateLimitException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, "rate_limit_error", "rate_limit_exceeded", message);
    }
}
