package ai.agent.errors;

import org.springframework.http.HttpStatus;

public class LlmUpstreamException extends ApiException {

    public LlmUpstreamException(String message) {
        super(HttpStatus.BAD_GATEWAY, "server_error", "server_error", message);
    }
}
