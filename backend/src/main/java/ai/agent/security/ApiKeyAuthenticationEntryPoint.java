package ai.agent.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes the 401: sync format {"error":{message,code}} or, under /v1/, the /v1 format
 * {"error":{message,type,param,code}}. Always JSON.
 */
public class ApiKeyAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        Object rejection = request.getAttribute(ApiKeyAuthFilter.REJECTION);
        String message = rejection != null ? rejection.toString() : ApiKeyAuthFilter.MISSING;

        Map<String, Object> error = new LinkedHashMap<>();
        error.put("message", message);
        if (isV1(request)) {
            error.put("type", "invalid_request_error");
            error.put("param", null);
        }
        error.put("code", "invalid_api_key");

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(MAPPER.writeValueAsString(Map.of("error", error)));
    }

    private static boolean isV1(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/v1/") || path.equals("/v1");
    }
}
