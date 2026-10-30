package ai.agent.memory;

import java.util.List;
import java.util.Map;

/**
 * Conversations shared by the sync contract and /v1 threads: in memory or in PostgreSQL
 * ({@code agent.store.type}). Every method on a missing thread throws NotFoundException,
 * except {@link #exists}.
 */
public interface ConversationStore {

    /** Thread data shown by /v1 (messages are read separately). */
    record ThreadInfo(String id, long createdAt, Map<String, String> metadata) {}

    /** One line of GET /threads/summaries; times in epoch seconds. */
    record ThreadSummary(String id, long createdAt, String assistantId, String title, int messageCount,
                         long lastActivityAt) {}

    default String create() {
        return create(Map.of());
    }

    String create(Map<String, String> metadata);

    ThreadInfo thread(String threadId);

    ThreadInfo setMetadata(String threadId, Map<String, String> metadata);

    boolean exists(String threadId);

    List<String> ids();

    /** Every thread, most recent activity first. */
    List<ThreadSummary> summaries();

    void delete(String threadId);

    List<StoredMessage> messages(String threadId);

    void append(String threadId, StoredMessage message);

    /** Tool calls of the last assistant message with tool calls that have no result yet. */
    List<ToolCall> pendingToolCalls(String threadId);

    /** Records {@code result} for every pending tool call, keeping the history valid for providers. */
    void closePendingToolCalls(String threadId, String result);

    /** Assistant of the last turn that requested tools (null: the global settings), as a run keeps its assistant. */
    String toolCallAssistant(String threadId);

    /** Records the result of one pending tool call, in the turn that requested it. */
    void appendToolResult(String threadId, ToolCall call, String result);

    /** Assistant tool-call turns since the last user message (tool rounds spent on the current input). */
    int toolRoundsSinceLastUserMessage(String threadId);
}
