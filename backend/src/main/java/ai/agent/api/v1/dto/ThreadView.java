package ai.agent.api.v1.dto;

import ai.agent.memory.ConversationStore;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.Map;

/** OpenAI "thread" object; {@code tool_resources} is always empty. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ThreadView(String id, String object, long createdAt, Map<String, String> metadata, Map<String, Object> toolResources) {

    public static ThreadView of(ConversationStore.ThreadInfo thread) {
        return new ThreadView(thread.id(), "thread", thread.createdAt(), thread.metadata(), Map.of());
    }
}
