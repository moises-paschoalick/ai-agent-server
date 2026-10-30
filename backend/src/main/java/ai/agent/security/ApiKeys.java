package ai.agent.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

/**
 * API keys accepted by this server ({@code agent.api-keys}, comma-separated). Independent from
 * the LLM provider keys. An empty list keeps the server up but rejects every protected route.
 */
@Slf4j
@Component
public class ApiKeys {

    private final List<byte[]> keys;

    public ApiKeys(@Value("${agent.api-keys:}") String configured) {
        this.keys = Arrays.stream(configured == null ? new String[0] : configured.split(","))
                .map(String::strip)
                .filter(k -> !k.isEmpty())
                .map(k -> k.getBytes(StandardCharsets.UTF_8))
                .toList();
        if (keys.isEmpty()) {
            log.warn("No API keys configured (AGENT_API_KEYS is empty): every protected route will answer 401");
        }
    }

    public boolean isEmpty() {
        return keys.isEmpty();
    }

    /** Constant-time comparison against every key, without stopping at the first match. */
    public boolean matches(String candidate) {
        if (candidate == null) {
            return false;
        }
        byte[] bytes = candidate.getBytes(StandardCharsets.UTF_8);
        boolean match = false;
        for (byte[] key : keys) {
            match |= MessageDigest.isEqual(key, bytes);
        }
        return match;
    }

    /** First 5 characters + "***" + last 3; only "***" for keys of 8 characters or fewer. */
    public static String mask(String key) {
        if (key == null || key.length() <= 8) {
            return "***";
        }
        return key.substring(0, 5) + "***" + key.substring(key.length() - 3);
    }
}
