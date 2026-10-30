package ai.agent.errors;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base for all domain errors. Carries every field used by the sync format
 * ({message, code}) and the /v1 format ({message, type, param, code}).
 */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String type;
    private final String code;
    private final String param;

    public ApiException(HttpStatus status, String type, String code, String param, String message) {
        super(message);
        this.status = status;
        this.type = type;
        this.code = code;
        this.param = param;
    }

    public ApiException(HttpStatus status, String type, String code, String message) {
        this(status, type, code, null, message);
    }
}
