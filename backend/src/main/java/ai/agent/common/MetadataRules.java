package ai.agent.common;

import ai.agent.errors.ValidationException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OpenAI metadata limits: at most 16 keys, keys up to 64 characters, values up to 512.
 */
public final class MetadataRules {

    static final int MAX_KEYS = 16;
    static final int MAX_KEY_LENGTH = 64;
    static final int MAX_VALUE_LENGTH = 512;

    private MetadataRules() {
    }

    /** Returns a defensive copy (empty when null) or throws 400 with {@code param} "metadata". */
    public static Map<String, String> validated(Map<String, String> metadata) {
        if (metadata == null) {
            return Map.of();
        }
        if (metadata.size() > MAX_KEYS) {
            throw new ValidationException("metadata can have at most " + MAX_KEYS + " keys", "metadata");
        }
        metadata.forEach((key, value) -> {
            if (key.length() > MAX_KEY_LENGTH) {
                throw new ValidationException("metadata keys must have at most " + MAX_KEY_LENGTH + " characters", "metadata");
            }
            if (value != null && value.length() > MAX_VALUE_LENGTH) {
                throw new ValidationException("metadata values must have at most " + MAX_VALUE_LENGTH + " characters", "metadata");
            }
        });
        return Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
