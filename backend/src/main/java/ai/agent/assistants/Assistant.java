package ai.agent.assistants;

import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * Reusable configuration for /v1 runs. {@code responseFormat} is kept as sent and ignored.
 */
public record Assistant(
        String id,
        long createdAt,
        String name,
        String description,
        String model,
        String instructions,
        List<AssistantTool> tools,
        Map<String, String> metadata,
        Double temperature,
        Double topP,
        JsonNode responseFormat) {

    public Assistant {
        tools = tools == null ? List.of() : List.copyOf(tools);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        if (responseFormat != null && responseFormat.isNull()) {
            responseFormat = null;
        }
    }
}
