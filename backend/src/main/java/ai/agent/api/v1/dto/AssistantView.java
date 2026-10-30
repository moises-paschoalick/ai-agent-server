package ai.agent.api.v1.dto;

import ai.agent.assistants.Assistant;
import ai.agent.assistants.AssistantTool;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.Map;

/** OpenAI "assistant" object; {@code tool_resources} is always empty (no code_interpreter or file_search). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AssistantView(
        String id,
        String object,
        long createdAt,
        String name,
        String description,
        String model,
        String instructions,
        List<AssistantTool> tools,
        Map<String, Object> toolResources,
        Map<String, String> metadata,
        Double temperature,
        Double topP,
        JsonNode responseFormat) {

    public static AssistantView of(Assistant a) {
        return new AssistantView(a.id(), "assistant", a.createdAt(), a.name(), a.description(), a.model(),
                a.instructions(), a.tools(), Map.of(), a.metadata(), a.temperature(), a.topP(), a.responseFormat());
    }
}
