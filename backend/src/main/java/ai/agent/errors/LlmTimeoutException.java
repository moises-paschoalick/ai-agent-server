package ai.agent.errors;

import org.springframework.http.HttpStatus;

public class LlmTimeoutException extends ApiException {

    public LlmTimeoutException(String message) {
        super(HttpStatus.GATEWAY_TIMEOUT, "server_error", "timeout", message);
    }
}
