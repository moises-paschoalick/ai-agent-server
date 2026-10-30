package ai.agent.errors;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Error body chosen by path: under /v1/ the OpenAI format {"error":{message,type,param,code}},
 * elsewhere the sync format {"error":{message,code}}. The Content-Type is set explicitly so the
 * body is JSON even when the client asks for HTML.
 */
@Slf4j
@RestControllerAdvice
public class ApiErrorAdvice {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException ex, HttpServletRequest request) {
        return body(request, ex.getStatus(), ex.getType(), ex.getCode(), ex.getParam(), ex.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        String message = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        return body(request, ex.getStatusCode(), null, codeFor(ex.getStatusCode()), null, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return body(request, HttpStatus.BAD_REQUEST, null, "invalid_request", null, "Request body is not valid JSON.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleInvalid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        var field = ex.getBindingResult().getFieldErrors().stream().findFirst();
        String message = field.map(error -> error.getField() + ": " + error.getDefaultMessage()).orElse("Invalid request.");
        return body(request, HttpStatus.BAD_REQUEST, null, "invalid_request", field.map(f -> f.getField()).orElse(null), message);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethod(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return body(request, HttpStatus.METHOD_NOT_ALLOWED, null, "method_not_allowed", null,
                "Method " + ex.getMethod() + " is not supported for this route.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return body(request, HttpStatus.NOT_FOUND, null, "not_found", null, "Route not found.");
    }

    /** Database down or unreachable (AGENT_STORE=postgres): generic message, details only in the log. */
    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<Map<String, Object>> handleStorage(RuntimeException ex, HttpServletRequest request) {
        log.warn("Storage unavailable: {}", ex.getMessage());
        return body(request, HttpStatus.SERVICE_UNAVAILABLE, "server_error", "service_unavailable", null,
                "Storage is unavailable. Try again later.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error", ex);
        return body(request, HttpStatus.INTERNAL_SERVER_ERROR, null, "server_error", null, "Internal server error.");
    }

    private static ResponseEntity<Map<String, Object>> body(HttpServletRequest request, HttpStatusCode status,
                                                            String type, String code, String param, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("message", message);
        if (isV1(request)) {
            error.put("type", type != null ? type : typeFor(status));
            error.put("param", param);
        }
        error.put("code", code);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("error", error));
    }

    static boolean isV1(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/v1/") || path.equals("/v1");
    }

    private static String typeFor(HttpStatusCode status) {
        if (status.value() == 429) {
            return "rate_limit_error";
        }
        return status.is5xxServerError() ? "server_error" : "invalid_request_error";
    }

    private static String codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "invalid_request";
            case 404 -> "not_found";
            case 405 -> "method_not_allowed";
            case 409 -> "conflict";
            case 429 -> "rate_limit_exceeded";
            case 504 -> "timeout";
            default -> "server_error";
        };
    }
}
