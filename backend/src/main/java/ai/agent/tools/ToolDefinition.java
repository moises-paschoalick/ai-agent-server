package ai.agent.tools;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A tool the model can call. {@code parameters} is the raw JSON Schema, kept as sent
 * (key order preserved) so it round-trips unchanged.
 */
public record ToolDefinition(String name, String description, Map<String, Object> parameters, boolean strict) {

    /** Schema used when a tool is created without parameters. */
    public static Map<String, Object> emptyParameters() {
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("type", "object");
        empty.put("properties", new LinkedHashMap<>());
        return empty;
    }

    /** Request body of POST/PUT: every field is optional at the JSON level. */
    public record Request(String name, String description, Map<String, Object> parameters, Boolean strict) {

        ToolDefinition toDefinition(String name) {
            return new ToolDefinition(name, description,
                    parameters != null ? parameters : emptyParameters(),
                    Boolean.TRUE.equals(strict));
        }
    }
}
