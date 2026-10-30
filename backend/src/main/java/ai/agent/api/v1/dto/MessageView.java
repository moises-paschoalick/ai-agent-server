package ai.agent.api.v1.dto;

import ai.agent.memory.StoredMessage;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.Map;

/** OpenAI "thread.message" object (text content only); stored messages are always complete. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MessageView(
        String id,
        String object,
        long createdAt,
        String threadId,
        String status,
        Object incompleteDetails,
        long completedAt,
        Long incompleteAt,
        String role,
        List<ContentPart> content,
        String assistantId,
        String runId,
        List<Object> attachments,
        Map<String, String> metadata) {

    public record ContentPart(String type, Text text) {}

    public record Text(String value, List<Object> annotations) {}

    public static MessageView of(StoredMessage m) {
        return new MessageView(m.id(), "thread.message", m.createdAt(), m.threadId(), "completed", null, m.createdAt(), null,
                m.role().name().toLowerCase(),
                List.of(new ContentPart("text", new Text(m.content(), List.of()))),
                m.assistantId(), m.runId(), List.of(), m.metadata());
    }
}
