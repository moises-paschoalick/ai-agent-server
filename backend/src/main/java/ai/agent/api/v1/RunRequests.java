package ai.agent.api.v1;

import ai.agent.common.MetadataRules;
import ai.agent.errors.ValidationException;
import ai.agent.memory.StoredMessage;
import ai.agent.runs.RunRequest;
import ai.agent.runs.RunService;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Parses /v1 run bodies into {@link RunRequest} (types only; business rules live in RunService). */
final class RunRequests {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, String>> STRINGS = new TypeReference<>() {};

    private RunRequests() {
    }

    static RunRequest parse(JsonNode body, String threadId) {
        JsonNode json = body == null ? MAPPER.createObjectNode() : body;
        List<StoredMessage> additional = new ArrayList<>();
        JsonNode messages = json.get("additional_messages");
        if (messages != null && !messages.isNull()) {
            if (!messages.isArray()) {
                throw new ValidationException("additional_messages must be a list", "additional_messages");
            }
            messages.forEach(m -> additional.add(MessageInput.parse(m).toStored(threadId)));
        }
        JsonNode stream = json.get("stream");
        return new RunRequest(
                text(json, "assistant_id"),
                text(json, "model"),
                text(json, "instructions"),
                text(json, "additional_instructions"),
                json.get("tools"),
                json.get("tool_choice"),
                bool(json, "parallel_tool_calls"),
                number(json, "temperature"),
                number(json, "top_p"),
                metadata(json.get("metadata")),
                additional,
                stream != null && stream.isBoolean() && stream.asBoolean());
    }

    static List<RunService.ToolOutput> toolOutputs(JsonNode body) {
        JsonNode outputs = body == null ? null : body.get("tool_outputs");
        if (outputs == null || !outputs.isArray()) {
            throw new ValidationException("tool_outputs must be a list", "tool_outputs");
        }
        List<RunService.ToolOutput> result = new ArrayList<>();
        for (JsonNode output : outputs) {
            JsonNode value = output.get("output");
            result.add(new RunService.ToolOutput(text(output, "tool_call_id"),
                    value == null || value.isNull() ? null : value.isString() ? value.stringValue() : value.toString()));
        }
        return result;
    }

    static Map<String, String> metadata(JsonNode metadata) {
        if (metadata == null || metadata.isNull()) {
            return Map.of();
        }
        if (!metadata.isObject()) {
            throw new ValidationException("metadata must be an object", "metadata");
        }
        return MetadataRules.validated(MAPPER.convertValue(metadata, STRINGS));
    }

    private static String text(JsonNode json, String field) {
        JsonNode value = json.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isString()) {
            throw new ValidationException(field + " must be a string", field);
        }
        return value.stringValue();
    }

    private static Double number(JsonNode json, String field) {
        JsonNode value = json.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isNumber()) {
            throw new ValidationException(field + " must be a number", field);
        }
        return value.doubleValue();
    }

    private static Boolean bool(JsonNode json, String field) {
        JsonNode value = json.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isBoolean()) {
            throw new ValidationException(field + " must be a boolean", field);
        }
        return value.asBoolean();
    }
}
