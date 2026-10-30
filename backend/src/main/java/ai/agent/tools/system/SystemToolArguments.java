package ai.agent.tools.system;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Reads the JSON arguments of a system tool, with messages the model can act on. */
final class SystemToolArguments {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JsonNode json;

    private SystemToolArguments(JsonNode json) {
        this.json = json;
    }

    static SystemToolArguments parse(String argumentsJson) {
        JsonNode json;
        try {
            json = argumentsJson == null || argumentsJson.isBlank() ? MAPPER.createObjectNode() : MAPPER.readTree(argumentsJson);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("arguments must be a JSON object");
        }
        if (!json.isObject()) {
            throw new IllegalArgumentException("arguments must be a JSON object");
        }
        return new SystemToolArguments(json);
    }

    /** Null when missing, null or blank. */
    String text(String field) {
        JsonNode value = json.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isString()) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        String text = value.stringValue().strip();
        return text.isEmpty() ? null : text;
    }

    String requiredText(String field) {
        String text = text(field);
        if (text == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return text;
    }

    long requiredLong(String field) {
        JsonNode value = json.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException(field + " is required");
        }
        if (!value.isIntegralNumber()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        return value.longValue();
    }

    static String toJson(Object value) {
        return MAPPER.writeValueAsString(value);
    }
}
