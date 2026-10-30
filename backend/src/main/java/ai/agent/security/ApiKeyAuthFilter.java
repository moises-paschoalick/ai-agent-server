package ai.agent.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ai.agent.audit.AuditContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates requests carrying {@code Authorization: Bearer <key>} with a configured key.
 * Otherwise it records why (for the 401 body) and lets authorization decide: open routes
 * still pass. Not a bean, so it only runs inside the security filter chain.
 */
@Slf4j
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    /** Request attribute with the 401 message for {@link ApiKeyAuthenticationEntryPoint}. */
    public static final String REJECTION = ApiKeyAuthFilter.class.getName() + ".rejection";

    static final String MISSING = "Missing API key. Send Authorization: Bearer <key>.";
    static final String NOT_CONFIGURED = "No API keys are configured on the server.";
    private static final String BEARER = "Bearer ";

    private final ApiKeys keys;

    public ApiKeyAuthFilter(ApiKeys keys) {
        this.keys = keys;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        String key = header != null && header.startsWith(BEARER) ? header.substring(BEARER.length()).strip() : null;
        String caller = null;

        if (keys.isEmpty()) {
            request.setAttribute(REJECTION, NOT_CONFIGURED);
        } else if (key == null || key.isEmpty()) {
            request.setAttribute(REJECTION, MISSING);
        } else if (keys.matches(key)) {
            SecurityContext context = SecurityContextHolder.getContextHolderStrategy().createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("api-key", null, List.of()));
            SecurityContextHolder.getContextHolderStrategy().setContext(context);
            caller = AuditContext.hash(key);
        } else {
            String masked = ApiKeys.mask(key);
            request.setAttribute(REJECTION, "Incorrect API key provided: " + masked);
            log.info("Rejected API key {} on {} {}", masked, request.getMethod(), request.getRequestURI());
        }
        String previous = AuditContext.enter(caller);
        try {
            chain.doFilter(request, response);
        } finally {
            AuditContext.exit(previous);
        }
    }
}
