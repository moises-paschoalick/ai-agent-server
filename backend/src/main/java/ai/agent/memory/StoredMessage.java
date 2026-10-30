package ai.agent.memory;

import ai.agent.common.Ids;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * One message of a conversation, in the application's own model. Converted to LangChain4j
 * messages on every model call, so ids and visibility stay under our control.
 */
public record StoredMessage(
        String id,
        String threadId,
        Role role,
        String content,
        List<ToolCall> toolCalls,
        String toolCallId,
        String toolName,
        String turnId,
        String runId,
        String assistantId,
        long createdAt,
        Map<String, String> metadata) {

    public enum Role { USER, ASSISTANT, TOOL }

    public StoredMessage {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /** Shown to clients: user messages and assistant text without tool calls. */
    public boolean visible() {
        return switch (role) {
            case USER -> true;
            case ASSISTANT -> toolCalls.isEmpty() && content != null && !content.isEmpty();
            case TOOL -> false;
        };
    }

    public static StoredMessage user(String threadId, String content) {
        return new StoredMessage(newId(), threadId, Role.USER, content, List.of(), null, null, null, null, null, now(), Map.of());
    }

    public static StoredMessage assistantText(String threadId, String content, String runId, String assistantId) {
        return new StoredMessage(newId(), threadId, Role.ASSISTANT, content, List.of(), null, null, null, runId, assistantId, now(), Map.of());
    }

    public static StoredMessage assistantToolCalls(String threadId, String content, List<ToolCall> toolCalls,
                                                   String turnId, String runId, String assistantId) {
        return new StoredMessage(newId(), threadId, Role.ASSISTANT, content == null ? "" : content, toolCalls,
                null, null, turnId, runId, assistantId, now(), Map.of());
    }

    public static StoredMessage toolResult(String threadId, ToolCall call, String result, String turnId, String runId) {
        return new StoredMessage(newId(), threadId, Role.TOOL, result, List.of(), call.id(), call.name(),
                turnId, runId, null, now(), Map.of());
    }

    /** Same message with the given metadata (used by /v1). */
    public StoredMessage withMetadata(Map<String, String> value) {
        return new StoredMessage(id, threadId, role, content, toolCalls, toolCallId, toolName, turnId, runId,
                assistantId, createdAt, value);
    }

    private static String newId() {
        return Ids.newId("msg_");
    }

    private static long now() {
        return Instant.now().getEpochSecond();
    }
}
